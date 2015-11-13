#include <opencv2/opencv.hpp>
#include <opencv2/ocl/ocl.hpp>
#include <opencv2/highgui/highgui.hpp>

#include <iostream>
#include <vector>
#include <cmath>

#include <opencv2/core/core.hpp>
#include "EvmGdownIIR.hpp"
#include "Pulse.hpp"
#include "Window.hpp"
#include "profiler/Profiler.h"
//#include "stabilization.cpp"

using namespace std;
using namespace cv;
using namespace cv::ocl;

static void writeVideo(VideoCapture& capture, const Mat& frame);

const int HORIZONTAL_BORDER_CROP = 20;

struct TransformParam
{
    TransformParam() {}
    TransformParam(double dx0, double dy0, double da0) {
        dx = dx0;
        dy = dy0;
        da = da0;
    }

    double dx; // x coordinate
    double dy; // y coordinate
    double da; // angle
};

struct Direction
{
    Direction() {}
    Direction(double x0, double y0, double a0) {
        x = x0;
        y = y0;
        a = a0;
    }

	Direction operator+(const Direction & d){
		return Direction(x+d.x, y+d.y, a+d.a);
	}

	Direction operator-(const Direction & d){
		return Direction(x-d.x, y-d.y, a-d.a);
	}

	Direction operator*(const Direction & d){
		return Direction(x*d.x, y*d.y, a*d.a);
	}

	Direction operator/(const Direction & d){
		return Direction(x/d.x, y/d.y, a/d.a);
	}

	Direction operator=(const Direction & d){
		x = d.x;
		y = d.y;
		a = d.a;
		return d;
	}

    double x; // x coordinate
    double y; // y coordinate
    double a; // angle
};


static void writeVideo(VideoCapture & capture, const Mat & frame) {
    static VideoWriter writer("out.avi", CV_FOURCC('X', 'V', 'I', 'D'), capture.get(CV_CAP_PROP_FPS),
            Size(capture.get(CV_CAP_PROP_FRAME_WIDTH), capture.get(CV_CAP_PROP_FRAME_HEIGHT)));
    writer << frame;
}

int main()
{
	VideoCapture cap("http://192.168.1.101:8090/live.mpg");
//	VideoCapture cap("live26.mpg");
	Mat frame,prevFrame;
	Mat frameData,prevFrameData;
	oclMat oclFrame,oclPrevFrame;
	oclMat oclFrameData,oclPrevFrameData;

    const bool shouldWrite = false;
    const bool shouldFlip = true;

	const int WIDTH  = cap.get(CV_CAP_PROP_FRAME_WIDTH);
    const int HEIGHT = cap.get(CV_CAP_PROP_FRAME_HEIGHT);
    const double FPS = cap.get(CV_CAP_PROP_FPS);
    cout << "SIZE: " << WIDTH << "x" << HEIGHT << endl;

    Pulse pulse;
    if (FPS != 0) {
        cout << "FPS: " << FPS << endl;
        pulse.fps = FPS;
    }

    pulse.load("res/lbpcascade_frontalface.xml");
    pulse.start(WIDTH, HEIGHT);

    Window window(pulse);

	cap>>prevFrame;


	//namedWindow("video",CV_WINDOW_AUTOSIZE);
	//namedWindow("stabilized video",CV_WINDOW_AUTOSIZE);

    double a = 0;
	double x = 0;
	double y = 0;
	Direction predictDir; //posteriori state estimate
	Direction actualDir; //priori estimate
	Direction predictDirError; // posteriori estimate error covariance
	Direction actualDirError; // priori estimate error covariance
	Direction gain; //gain
	Direction z; //actual measurement
	double pstd = 4e-3;//can be changed
	double cstd = 0.25;//can be changed
	Direction noiseCov(pstd,pstd,pstd);// process noise covariance
	Direction noiseCovMeasure(cstd,cstd,cstd);// measurement noise covariance

	int counter=1;
	int vert_border = HORIZONTAL_BORDER_CROP * prevFrame.rows / prevFrame.cols;
	Mat rigidtransform,last_rigidtransform;
	//Stabilization stabilization = Stabilization(prevFrame);

	while(true)
	{

        cvtColor(prevFrame,prevFrameData,CV_BGR2GRAY);
        oclPrevFrame.upload(prevFrame);
        oclPrevFrameData.upload(prevFrameData);

	    PROFILE_SCOPED_DESC("loop");

        PROFILE_START_DESC("capture");
        cap >> frame;
        PROFILE_STOP();
		if(frame.empty())
			break;

        char c = (char)waitKey(50);
        if (c == 30) break;

        cvtColor(frame,frameData,CV_BGR2GRAY);
		oclFrame.upload(frame);
		oclFrameData.upload(frameData);

		vector<Point2f> prevpts,currpts;
		vector<Point2f> prev_corner, cur_corner;
		Mat status,err,corners;
		oclMat prev_corners,curr_corners;

		ocl::GoodFeaturesToTrackDetector_OCL(300)(oclPrevFrameData,prev_corners);
		prev_corners.download(corners);
		corners.row(0).copyTo(prevpts);
		calcOpticalFlowPyrLK(prevFrameData,frameData,prevpts,currpts,status,err);

		// weed out bad matches
		for(int counter1=0; counter1 < status.rows; counter1++) {
			if((int)status.at<uchar>(counter1,0)==1) {
				prev_corner.push_back(prevpts[counter1]);
				cur_corner.push_back(currpts[counter1]);
			}
		}

		//cout << prev_corner;
		//cout << cur_corner;

        //cout << estimateRigidTransform(prev_corner, cur_corner, false);

		Mat rigidtrans=estimateRigidTransform(prev_corner,cur_corner,false);

//        if (k < 2) {
//            cout << prev_corner << '\t' << cur_corner << '\n';
//        }

		if(rigidtrans.empty())
		{
			rigidtrans=last_rigidtransform.clone();
		}
		last_rigidtransform=rigidtrans.clone();

			double dx = rigidtrans.at<double>(0,2);
			double dy = rigidtrans.at<double>(1,2);
			double da = atan2(rigidtrans.at<double>(1,0), rigidtrans.at<double>(0,0));

			x += dx;
			y += dy;
			a += da;

			z = Direction(x,y,a);
			if(counter==1)
			{
				// intial guesses
				predictDir = Direction(0,0,0); //Initial estimate,  set 0
				predictDirError =Direction(1,1,1); //set error variance,set 1
			}
			else
			{
				//time update prediction
				actualDir = predictDir; //actualDir(k) = predictDir(k-1);
				actualDirError = predictDirError+noiseCov; //actualDirError(k) = predictDirError(k-1)+noiseCov;
				// measurement update correction
				gain = actualDirError/( actualDirError+noiseCovMeasure ); //gain;gain(k) = actualDirError(k)/( actualDirError(k)+noiseCovMeasure );
				predictDir = actualDir+gain*(z-actualDir); //z-actualDir is residual,predictDir(k) = actualDir(k)+gain(k)*(z(k)-actualDir(k));
				predictDirError = (Direction(1,1,1)-gain)*actualDirError; //predictDirError(k) = (1-gain(k))*actualDirError(k);
			}

			double diff_x = predictDir.x - x;
			double diff_y = predictDir.y - y;
			double diff_a = predictDir.a - a;

			dx = dx + diff_x;
			dy = dy + diff_y;
			da = da + diff_a;

			rigidtrans.at<double>(0,0) = cos(da);
			rigidtrans.at<double>(0,1) = -sin(da);
			rigidtrans.at<double>(1,0) = sin(da);
			rigidtrans.at<double>(1,1) = cos(da);

			rigidtrans.at<double>(0,2) = dx;
			rigidtrans.at<double>(1,2) = dy;

			oclMat dfinal_frame;
			Mat final_frame;
			ocl::warpAffine(oclPrevFrame,dfinal_frame,rigidtrans,Size(640,480));
			dfinal_frame.download(final_frame);

		//final_frame = stabilize(frame, prevFrame, counter);

			final_frame = final_frame(Range(vert_border, final_frame.rows-vert_border), Range(HORIZONTAL_BORDER_CROP, final_frame.cols-HORIZONTAL_BORDER_CROP));

			// Resize cur2 back to cur size, for better side by side comparison
			//cv::resize(final_frame, final_frame, frame.size());

		//Mat roi=final_frame(Range(50,430),Range(50,590));
		//imshow("video",frame);
		//imshow("stabilized video",final_frame);

        waitKey(1);
		prevFrame=frame.clone();
		frameData.copyTo(prevFrameData);
		oclPrevFrame.upload(prevFrame);
		oclPrevFrameData.upload(prevFrameData);
		counter++;

		window.update(final_frame);


        if (shouldWrite) {
            writeVideo(cap, final_frame);
        }

        PROFILE_START_DESC("wait key");
        if (waitKey(1) == 27) {
            PROFILE_STOP(); // wait key
            break;
        }
        PROFILE_STOP();

	}

	return 0;
}
