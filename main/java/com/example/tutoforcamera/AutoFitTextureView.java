package com.example.tutoforcamera;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.TextureView;

public class AutoFitTextureView extends TextureView {
    // Ratio of width to height of the view
    private int mRatioWidth = 0;
    private int mRatioHeight = 0;

    // Interface to get zoom characteristics from the camera
    public getZoomCaracteristics getZoomCaracteristics;

    // Boolean to keep track if it's the first time measuring
    boolean firstMeasure;

    // Rectangles to hold camera zoom area
    Rect cameraRecteub;
    public Rect cameraRecteub1;

    // Float to hold maximum zoom level
    float maxZoomTeub;

    // Integer to keep track of current zoom level
    int zoom_level;

    // Scale gesture detector to detect pinch zoom gestures
    private ScaleGestureDetector mScaleDetector;

    // Float to hold the current scale factor
    private float mScaleFactor = 3.f;

    // Boolean to keep track if it's the first time getting the max zoom level
    private boolean isfirstmaxzoomteub;

    // Constructor with single argument
    public AutoFitTextureView(Context context) {
        this(context, null);

        // Initialize some variables
        this.getZoomCaracteristics = null;
        this.firstMeasure=true;
        this.zoom_level=0;
        this.isfirstmaxzoomteub=true;

        // Create a new scale gesture detector
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    public AutoFitTextureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
        this.getZoomCaracteristics = null;
        this.firstMeasure=true;
        this.zoom_level=0;
        this.isfirstmaxzoomteub=true;
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    public AutoFitTextureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.getZoomCaracteristics = null;
        this.firstMeasure=true;
        this.zoom_level=0;
        this.isfirstmaxzoomteub=true;
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
    }


    // Interface to get zoom characteristics from the camera
    public interface getZoomCaracteristics{

        // Method to get the zoom rectangle from the camera
        Rect giveRectZoom() throws CameraAccessException;

        // Method to get the maximum zoom level from the camera
        float giveMaxZoom() throws CameraAccessException;

        // Method to set the zoom level for preview
        void previewRequestINT(Rect rect);

        // Method to create a capture session with the current zoom level
        void captureSession() throws CameraAccessException;

    }

    // Method to set the interface to get zoom characteristics from the camera
    public void setGetZoomCaracteristics(getZoomCaracteristics getZoomCaracteristics){
        this.getZoomCaracteristics=getZoomCaracteristics;
    }

    // Method to set the aspect ratio of the view
    public void setAspectRatio(int width, int height) {
        // Check if width and height are non-negative values, if not, throw an exception with a message
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Size cannot be negative.");
        }
        // Assign the width and height values to the class variables
        mRatioWidth = width;
        mRatioHeight = height;
        // Request a layout to update the view based on the new aspect ratio
        requestLayout();
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        // Gets the measured width and height values from the passed in widthMeasureSpec and heightMeasureSpec
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (0 == mRatioWidth || 0 == mRatioHeight) {
            // If either mRatioWidth or mRatioHeight is 0, then set the dimensions to the passed in values
            setMeasuredDimension(width, height);
        } else {
            // Calculate the ratio of width and height to mRatioWidth and mRatioHeight and set the dimension accordingly
            if (width < height * mRatioWidth / mRatioHeight) {
                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
            } else {
                setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
            }
        }
        // Increments the counter variable

    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Pass touch event to scale gesture detector
        mScaleDetector.onTouchEvent(event);
        return true;
    }


    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            // Get maximum zoom and current zoom rectangle if they have not been retrieved yet
            if (getZoomCaracteristics!=null && isfirstmaxzoomteub){
                try {
                    maxZoomTeub=  Math.min(3f,getZoomCaracteristics.giveMaxZoom());
                    isfirstmaxzoomteub=false;
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
                try {
                    cameraRecteub=getZoomCaracteristics.giveRectZoom();
                    cameraRecteub1=getZoomCaracteristics.giveRectZoom();

                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
// Get the scale factor from the gesture detector
            mScaleFactor = detector.getScaleFactor(); // Compute the eventual width after scaling
            float eventualWidth=cameraRecteub.width()/mScaleFactor;
            if (mScaleFactor>1){
                if (eventualWidth<cameraRecteub1.width()/3.0){ // If the eventual width is less than one third of the maximum width

                    cameraRecteub.set((int) ( 0.5f*(2*cameraRecteub1.width()/3.0)),
                            (int) ( 0.5f*(2*cameraRecteub1.height()/3)),
                            (int) ( 0.5f*(4*cameraRecteub1.width()/3)),
                            (int) ( 0.5f*(4*cameraRecteub1.height()/3)));

                }
                else{ // If the eventual width is greater than or equal to one third of the maximum width
                    cameraRecteub.set((int) ( 0.5f*(cameraRecteub1.width()-cameraRecteub.width()/mScaleFactor)),
                            (int) ( 0.5f*(cameraRecteub1.height()-cameraRecteub.height()/mScaleFactor)),
                            (int) ( 0.5f*(cameraRecteub1.width()+cameraRecteub.width()/mScaleFactor)),
                            (int) ( 0.5f*(cameraRecteub1.height()+cameraRecteub.height()/mScaleFactor)));
                }

            }
            else{ // If scaling down

                if (eventualWidth>cameraRecteub1.width()){
                    cameraRecteub.set(cameraRecteub1);
                }
                else{  // If the eventual width is less than or equal to the maximum width
                    cameraRecteub.set((int) ( 0.5f*(cameraRecteub1.width()-cameraRecteub.width()/mScaleFactor)),
                            (int) ( 0.5f*(cameraRecteub1.height()-cameraRecteub.height()/mScaleFactor)),
                            (int) ( 0.5f*(cameraRecteub1.width()+cameraRecteub.width()/mScaleFactor)),
                            (int) (( 0.5f*(cameraRecteub1.height()+cameraRecteub.height()/mScaleFactor))));
                }}

            if(getZoomCaracteristics!=null){
                getZoomCaracteristics.previewRequestINT(cameraRecteub);

            }
            if(getZoomCaracteristics!=null){
                try {
                    getZoomCaracteristics.captureSession();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }}
            return true;
        }
    }

}