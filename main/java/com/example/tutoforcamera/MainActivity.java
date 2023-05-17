package com.example.tutoforcamera;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.WindowMetrics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {


    // SparseIntArray that maps device orientation to degrees
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    // Assigning device orientation to degrees
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    // States of the camera state machine
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAITING_LOCK = 1;
    private static final int STATE_WAITING_PRECAPTURE = 2;
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;
    private static final int STATE_PICTURE_TAKEN = 4;

    // Maximum preview size of the camera
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;



    // Array of available camera services
    private CameraService[] cameraServiceList;

    // Index of the currently opened camera (default to zero)
    private int openedCamera=0;

    // Texture view to display the camera preview
    private AutoFitTextureView mTextureView;

    // Size of the camera preview
    private Size mPreviewSize;

    // Background thread for camera operations
    private HandlerThread mBackgroundThread;

    // Handler for the background thread
    private Handler mBackgroundHandler;

    // Image reader to capture still images
    private ImageReader mImageReader;

    // Byte array to store the captured image
    public byte[] byteArrayImage;

    // Builder for the camera preview request
    private CaptureRequest.Builder mPreviewRequestBuilder;

    // Camera preview request
    private CaptureRequest mPreviewRequest;

    // Current state of the camera state machine
    private int mState = STATE_PREVIEW;

    // Semaphore used to lock the camera while in use

    private Semaphore mCameraOpenCloseLock = new Semaphore(1);
    /*A Semaphore is a synchronization primitive in Java that is used to control access to a shared resource.
    The count of a Semaphore represents the number of permits available to access the shared resource.
    In this case, the Semaphore is being used to control access to a camera resource.
    The count of 1 means that only one thread can access the camera resource at a time,
    so this Semaphore is being used to enforce mutual exclusion and prevent multiple threads
    from accessing the camera resource simultaneously.The Semaphore will be acquired (decremented)
    when a thread requests access to the camera,
    and it will be released (incremented) when the thread is finished using the camera.*/

    // Boolean to indicate if the device's flash is supported
    private boolean mFlashSupported;

    // Orientation of the camera sensor
    private int mSensorOrientation;

    // Flash mode, 0 for off, 1 for auto, 2 for always on
    private int flashMode;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Classic Android, you have to find your view by ID
        mTextureView=findViewById(R.id.autoFitTextureView);


        startBackgroundThread(); // Go check below what this method does (it launches the thread to use the camera

        if (mTextureView.isAvailable()) {
            try {
                // Launch camera if the textureView is ready
                openCamera(mTextureView.getWidth(), mTextureView.getHeight());
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        } else {
            // Wait for the texture View to be ready to launch camera
            mTextureView.setSurfaceTextureListener(autoFitTextureListener);
        }

    }



    // Variable to set texture view
    private final TextureView.SurfaceTextureListener autoFitTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {

            try {
                openCamera(width, height);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            configureTransform(width, height);
        }
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };


    private final CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened. We release the lock and set the camera device
            // for the current camera service, then create a camera preview session.
            mCameraOpenCloseLock.release();
            cameraServiceList[openedCamera].mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is disconnected. We release the lock, close the camera device,
            // and set the camera device for the current camera service to null.
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            cameraServiceList[openedCamera].mCameraDevice  = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            // This method is called when an error occurs with the camera device. We release the lock, close the camera device,
            // set the camera device for the current camera service to null, and finish the activity.
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            cameraServiceList[openedCamera].mCameraDevice  = null;
            finish();

        }
    };


    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            return choices[0];
        }
    }


    private CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {
        private void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // To be update in the next part for taking pictures
                    break;
                }
                case STATE_WAITING_LOCK: {
                          // To be update in the next part for taking pictures
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // To be update in the next part for taking pictures

                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // To be update in the next part for taking pictures
                    break;
                }
            }
        }
        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }
    };

    private void setUpCameraOutputs(int width, int height) throws CameraAccessException {
        Activity activity = this;
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        // Get the list of available cameras:
        cameraServiceList= new CameraService[manager.getCameraIdList().length];
        mTextureView.setGetZoomCaracteristics(new AutoFitTextureView.getZoomCaracteristics() {
            @Override
            public Rect giveRectZoom() throws CameraAccessException {
                CameraManager manager12 = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                CameraCharacteristics characteristics = manager12.getCameraCharacteristics(cameraServiceList[openedCamera].CameraIDD);
                return characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            }

            @Override
            public float giveMaxZoom() throws CameraAccessException {
                CameraManager manager12 = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                CameraCharacteristics characteristics = manager12.getCameraCharacteristics(cameraServiceList[openedCamera].CameraIDD);
                return characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)*10 ;
            }

            @Override
            public void previewRequestINT(Rect rect) {
                mPreviewRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, rect);
            }

            @Override
            public void captureSession() throws CameraAccessException {
                cameraServiceList[openedCamera].captureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null,
                        mBackgroundHandler);
            }
        });
        // Retrieve information about each camera:
        try {
            for (String cameraId : manager.getCameraIdList()) {
                boolean poscam=true; // true if camera is front-facing, false otherwise
                // Retrieve the characteristics of the camera
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    poscam=true;
                    // poscam is true if it looks towards the face :)
                }
                else if (facing ==  CameraCharacteristics.LENS_FACING_BACK)
                {
                    poscam=false;
                    // poscam is false if we use the back camera
                }

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                     /*The if (map == null) condition checks if the
                     CameraCharacteristics for the current camera ID is null. If it is null,
                     the loop continues to the next camera ID, thereby skipping any further
                     processing for that particular camera.*/
                    continue;
                }

                // this allows as to see the largest dimensions we can get
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());

// Find out if we need to swap dimension to get the preview size relative to sensor coordinate.
                int displayRotation = activity.getDisplay().getRotation();

                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                }

                // Get the dimensions of the application window using WindowMetrics
                WindowMetrics windowMetrics = activity.getWindowManager().getCurrentWindowMetrics();
                Rect bounds = windowMetrics.getBounds();
                int display_X = bounds.width();
                int display_Y = bounds.height();
                // Initialize variables for preview size
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = display_X;
                int maxPreviewHeight = display_Y;

                // Swap dimensions if needed
                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = display_X;
                    maxPreviewHeight = display_Y;
                }

                // Limit the max preview width and height
                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                // Choose the optimal preview size based on the available sizes and the desired dimensions.
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest);

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView.setAspectRatio(
                            mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    mTextureView.setAspectRatio(
                            mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                // Check if the flash is supported.
                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                mFlashSupported = available == null ? false : available;

                // Initialize the CameraService for the current camera.
                cameraServiceList[Integer.parseInt(cameraId)]=new CameraService(cameraId,poscam);
                cameraServiceList[Integer.parseInt(cameraId)].setSensorOrientation(mSensorOrientation);
                cameraServiceList[Integer.parseInt(cameraId)].setFlashSupported(mFlashSupported);


            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {

        }
    }


    public class CameraService{
        String CameraIDD;  // A unique identifier for the camera
        CameraDevice mCameraDevice; // A reference to the CameraDevice object
        boolean frontOrBackCamera; // A boolean to indicate if the camera is front-facing or back-facing
        boolean flashSupported; // A boolean to indicate if the camera has a flash
        CameraCaptureSession captureSession; // A reference to the CameraCaptureSession object
        int sensorOrientation; // An integer to indicate the orientation of the camera sensor

        // Getter and setter methods for the member variables
        public int getSensorOrientation() {
            return sensorOrientation;
        }

        public void setSensorOrientation(int sensorOrientation) {
            this.sensorOrientation = sensorOrientation;
        }

        public String getCameraIDD() {
            return CameraIDD;
        }

        public void setCameraIDD(String cameraIDD) {
            CameraIDD = cameraIDD;
        }

        public CameraDevice getmCameraDevice() {
            return mCameraDevice;
        }

        public void setmCameraDevice(CameraDevice mCameraDevice) {
            this.mCameraDevice = mCameraDevice;
        }

        public boolean isFrontOrBackCamera() {
            return frontOrBackCamera;
        }

        public void setFrontOrBackCamera(boolean frontOrBackCamera) {
            this.frontOrBackCamera = frontOrBackCamera;
        }

        public boolean isFlashSupported() {
            return flashSupported;
        }

        public void setCaptureSession(CameraCaptureSession captureSession) {
            this.captureSession = captureSession;
        }

        public CameraCaptureSession getCaptureSession() {
            return captureSession;
        }

        // Constructor to initialize the member variables
        public CameraService(String cameraIDD, boolean frontOrBackCamera) {
            this.CameraIDD = cameraIDD;
            this.frontOrBackCamera = frontOrBackCamera;
        }

        public void setFlashSupported(boolean flashSupported) {
            this.flashSupported = flashSupported;
        }
    }


    private void openCamera(int width, int height) throws CameraAccessException {
        // Check if the app has permission to access the camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // If not, return and do not proceed with opening the camera
            return;
        }

        // Set up camera outputs and transform
        setUpCameraOutputs(width, height);
        configureTransform(width, height);

        // Get an instance of the CameraManager
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            // Use a lock to ensure proper opening and closing of the camera
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            // Open the camera with the given camera ID and set callbacks to handle camera events
            manager.openCamera(cameraServiceList[openedCamera].CameraIDD, cameraStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != cameraServiceList[openedCamera].captureSession) {
                cameraServiceList[openedCamera].captureSession.close();
                cameraServiceList[openedCamera].captureSession = null;
            }
            if (null != cameraServiceList[openedCamera].mCameraDevice) {
                cameraServiceList[openedCamera].mCameraDevice.close();
                cameraServiceList[openedCamera].mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        /* this is to allow the camera opperations to run on a separate thread and avoid blocking the UI*/
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        /* this is to communicate with the thread*/
    }



    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void createCameraPreviewSession() {
        try {

            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = cameraServiceList[openedCamera].mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            mPreviewRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.

            cameraServiceList[openedCamera].mCameraDevice.createCaptureSession(Arrays.asList(surface),
                    new CameraCaptureSession.StateCallback() {
                /* the createCaptureSession is deprecated, as of writing this tutorial, the code provided still works.
                  The new way of implementing this method is through createCaptureSession(SessionConfiguration).*/

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed

                            if (null == cameraServiceList[openedCamera].mCameraDevice) {

                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            cameraServiceList[openedCamera].captureSession=cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);


                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                cameraServiceList[openedCamera].captureSession.setRepeatingRequest(mPreviewRequest,null, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = this;

        // Check if TextureView, preview size or activity are null, then return
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }

        // Get the current rotation of the display
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();

        // Create a new Matrix object
        Matrix matrix = new Matrix();

        // Create a rectangle representing the view bounds
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);

        // Create a rectangle representing the preview size
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());

        // Calculate the center point of the view bounds
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();

        // If the rotation is 90 or 270 degrees, adjust the buffer rectangle
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());

            // Scale the preview to fill the view, then rotate it
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);

            // Store the rotation angle in a variable
            int rot= 90 * (rotation - 2);
        }
        // If the rotation is 180 degrees, just rotate the preview
        else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }

        // Apply the matrix to the TextureView
        mTextureView.setTransform(matrix);
    }



    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }


    private void updatePreview() {
        if (null == cameraServiceList[openedCamera].mCameraDevice) {
            return;
        }
        try {
            setUpCaptureRequestBuilder(mPreviewRequestBuilder);
            cameraServiceList[openedCamera].captureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

}