/**
 * Copyright 2018 Ricoh Company, Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.theta360.pluginapplication;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.theta360.pluginapplication.task.TakePictureTask;
import com.theta360.pluginapplication.task.TakePictureTask.Callback;
import com.theta360.pluginlibrary.activity.PluginActivity;
import com.theta360.pluginlibrary.callback.KeyCallback;
import com.theta360.pluginlibrary.receiver.KeyReceiver;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends PluginActivity {

    Button takePictureButton;
    ImageView thetaImageView;
    TextView statusTextView;
    // on the RICOH THETA V, there is no function button. People often use the
    // wifi button on the side of the camera to process images or change settings
    Button processButton;
    String extStorageDirectory = Environment.getExternalStorageDirectory().toString();
    String basepath = extStorageDirectory + "/DCIM/100RICOH/";

    String picturePath;
    private ExecutorService imageExecutor = Executors.newSingleThreadExecutor();
    private ExecutorService thetaExecutor = Executors.newSingleThreadExecutor();

    private File                   mCascadeFile;
    private CascadeClassifier      mJavaDetector;

    private float                  mRelativeFaceSize   = 0.2f;
    private int                    mAbsoluteFaceSize   = 0;

    URL inputFileUrl;
    private final String TAG = "THETADEBUG";

    int imageNumber = 0;

    private TakePictureTask.Callback mTakePictureTaskCallback = new Callback() {
        @Override
        public void onTakePicture(String fileUrl) {

        }
    };

    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    initializeOpenCVDependencies();
                    Log.i(TAG, "OpenCV loaded successfully");
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    private void initializeOpenCVDependencies() {
        try {
            // load cascade file from application resources
            InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            mCascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");
            FileOutputStream os = new FileOutputStream(mCascadeFile);

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            is.close();
            os.close();

            mJavaDetector = new CascadeClassifier(mCascadeFile.getAbsolutePath());
            if (mJavaDetector.empty()) {
                Log.e(TAG, "Failed to load cascade classifier");
                mJavaDetector = null;
            } else
                Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());

            cascadeDir.delete();

        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        thetaImageView = (ImageView)findViewById(R.id.thetaImageId);
        takePictureButton = (Button)findViewById(R.id.takePictueButtonId);
        processButton = (Button)findViewById(R.id.processButtonId);
        thetaImageView = (ImageView)findViewById(R.id.thetaImageId);
        thetaImageView.setImageResource(R.drawable.theta);

        checkPermission();

        File thetaMediaDir = new File(basepath);
        if (!thetaMediaDir.exists()) {
            thetaMediaDir.mkdirs();
        }

        takePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                picturePath = takeThetaPicture();
                Log.d(TAG, "received image path " + picturePath);

                /**
                 * Call your image processing or file transfer method here or
                 * trigger it with a button press.
                 * If you want to process your image when the picture is taken,
                 * uncomment the line below.
                 */
                // processImage(picturePath);


            }
        });

        processButton.setOnClickListener(new View.OnClickListener() {
            /**
             * This section is only if you want to trigger your image
             * processing or file transfer when a button is pressed
             * on the camera.  If you start the image process when the
             * picture is taken, you can delete the entire method.
             * @param v
             */
            @Override
            public void onClick(View v) {
                processImage(picturePath);
                Toast.makeText(MainActivity.this, "Processed image: " +
                        picturePath, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    @Override
    protected void onPause() {
        // Do end processing
        //close();

        super.onPause();
    }
    private void processImage(String thetaPicturePath) {


        // load the picture from the drawable resource
        //Bitmap img = BitmapFactory.decodeResource(getResources(), R.drawable.park);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 4;
        Log.d(TAG, thetaPicturePath);
        Bitmap img = BitmapFactory.decodeFile(thetaPicturePath, options);
        Mat src = new Mat (img.getWidth(), img.getHeight(), CvType.CV_8UC4);
        Mat dst = new Mat (img.getWidth(), img.getHeight(), CvType.CV_8UC1);
        Utils.bitmapToMat(img, src);

        Imgproc.cvtColor(src, dst, Imgproc.COLOR_BGR2GRAY);
        MatOfRect faces = new MatOfRect();

        if (mAbsoluteFaceSize == 0) {
            int height = src.rows();
            if (Math.round(height * mRelativeFaceSize) > 0) {
                mAbsoluteFaceSize = Math.round(height * mRelativeFaceSize);
            }
        }

        // Use the classifier to detect faces
        if (mJavaDetector != null) {
            mJavaDetector.detectMultiScale(dst, faces, 1.1, 2, 2,
                    new Size(mAbsoluteFaceSize, mAbsoluteFaceSize), new Size());
        }

        // If there are any faces found, draw a rectangle around it
        Rect[] facesArray = faces.toArray();
        if (facesArray.length == 0) {
            Toast.makeText(this, "No faces found!", Toast.LENGTH_SHORT).show();
        } else {
            for (int i = 0; i < facesArray.length; i++)
                Imgproc.rectangle(dst, facesArray[i].tl(), facesArray[i].br(), new Scalar(0, 255, 0, 255), 3);
        }

        Core.flip(dst, src, 1);

        Utils.matToBitmap(src, img);

        thetaImageView.setImageBitmap(img);

    }

    public String takeThetaPicture() {

        InputStream in = null;
        OutputStream out = null;
        String thetaImagePath = null;
        AssetManager assetManager = getResources().getAssets();

        String[] thetaImageFiles = null;

        try {
            thetaImageFiles =  assetManager.list("100RICOH");
        } catch (IOException e) {
            e.printStackTrace();
        }


        try {
            if (imageNumber >= thetaImageFiles.length) {
                imageNumber = 0;
                Log.d(TAG, "Set Image Number to Zero");
            }

            // copy file
            in = assetManager.open("100RICOH/" + thetaImageFiles[imageNumber]);
            out = new FileOutputStream(basepath  + thetaImageFiles[imageNumber]);
            copyFile(in, out);

            in.close();
            in = null;
            out.flush();
            out.close();
            out= null;
            Log.d(TAG, "copied file " + thetaImageFiles[imageNumber]);

            InputStream inputStream = assetManager.open("100RICOH/" + thetaImageFiles[imageNumber]);
            Drawable d = Drawable.createFromStream(inputStream, null);
            thetaImageView.setImageDrawable(d);
            inputStream.close();
            inputStream = null;
            thetaImagePath = basepath + thetaImageFiles[imageNumber];

            // increment image number last
            imageNumber = imageNumber + 1;

        } catch (IOException e) {
            e.printStackTrace();
        }

        return thetaImagePath;


    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    public String getImagePath() {
        String[] parts = inputFileUrl.toString().split("/");
        int length = parts.length;
        String filepath = Environment.getExternalStorageDirectory().getPath() +
                "/DCIM/100RICOH/" +
                parts[length - 1];
        Log.d(TAG, filepath);
        return filepath;
    }

    public void checkPermission() {
        statusTextView = (TextView) findViewById(R.id.statusViewId);
        if ((ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) &&
                (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.READ_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_GRANTED)) {
            statusTextView.setText("Ready");
            Toast.makeText(this, "storage permission good", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "WARNING: Need to enable storage permission",
                    Toast.LENGTH_LONG).show();
            statusTextView.setText("Check Permissions");
        }
    }
}
