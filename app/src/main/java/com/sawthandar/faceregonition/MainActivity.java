package com.sawthandar.faceregonition;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.YuvImage;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.TypefaceSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.LruCache;
import android.util.Pair;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.ImageLoader;
import com.sawthandar.faceregonition.adapter.FaceTokenAdapter;
import com.sawthandar.faceregonition.adapter.GroupNameAdapter;
import com.sawthandar.faceregonition.camera.CameraManager;
import com.sawthandar.faceregonition.camera.CameraPreview;
import com.sawthandar.faceregonition.camera.CameraPreviewData;
import com.sawthandar.faceregonition.camera.ComplexFrameHelper;
import com.sawthandar.faceregonition.utils.FileUtil;

import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ByteArrayBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.util.CharsetUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;

import mcv.facepass.FacePassException;
import mcv.facepass.FacePassHandler;
import mcv.facepass.auth.AuthApi.AuthApi;
import mcv.facepass.auth.AuthApi.AuthApplyResponse;
import mcv.facepass.auth.AuthApi.ErrorCodeConfig;
import mcv.facepass.types.FacePassAddFaceResult;
import mcv.facepass.types.FacePassAgeGenderResult;
import mcv.facepass.types.FacePassConfig;
import mcv.facepass.types.FacePassDetectionResult;
import mcv.facepass.types.FacePassFace;
import mcv.facepass.types.FacePassImage;
import mcv.facepass.types.FacePassImageRotation;
import mcv.facepass.types.FacePassImageType;
import mcv.facepass.types.FacePassModel;
import mcv.facepass.types.FacePassPose;
import mcv.facepass.types.FacePassRCAttribute;
import mcv.facepass.types.FacePassRecognitionResult;
import mcv.facepass.types.FacePassRecognitionState;
import mcv.facepass.types.FacePassTrackOptions;

public class MainActivity extends AppCompatActivity implements CameraManager.CameraListener, View.OnClickListener {

    private enum FacePassSDKMode {
        MODE_ONLINE,
        MODE_OFFLINE
    }

    private enum FacePassCameraType {
        FACEPASS_SINGLECAM,
        FACEPASS_DUALCAM
    }

    private enum FacePassAuthType {
        FASSPASS_AUTH_MCVFACE,
        FACEPASS_AUTH_MCVSAFE
    }

    private static final FacePassSDKMode SDK_MODE = FacePassSDKMode.MODE_OFFLINE;

    private static final String DEBUG_TAG = "FacePassDemo";

    // Customers need to configure according to their own needs
    private static final String authIP = "https://api-cn.faceplusplus.com";
    public static final String apiKey = "";
    public static final String apiSecret = "";

    public static final String CERT_PATH = "Download/CBG_Android_Face_Reco---77-Trial-one-stage.cert";

    /* Configure monocular/binocular scenes according to requirements, the default is monocular */
    private static final FacePassCameraType CamType = FacePassCameraType.FACEPASS_SINGLECAM;

    /* Configure and authorize mcvface / mcvsafe according to requirements, the default is mcvface */
    private static final FacePassAuthType authType = FacePassAuthType.FASSPASS_AUTH_MCVFACE;

    /* Face Recognition Group */
    private static final String group_name = "facepass";

    /* Permissions required by the program: camera file storage network access */
    private static final int PERMISSIONS_REQUEST = 1;
    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    private static final String PERMISSION_WRITE_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private static final String PERMISSION_READ_STORAGE = Manifest.permission.READ_EXTERNAL_STORAGE;
    private static final String PERMISSION_INTERNET = Manifest.permission.INTERNET;
    private static final String PERMISSION_ACCESS_NETWORK_STATE = Manifest.permission.ACCESS_NETWORK_STATE;
    private final String[] Permission = new String[]{PERMISSION_CAMERA, PERMISSION_WRITE_STORAGE, PERMISSION_READ_STORAGE, PERMISSION_INTERNET, PERMISSION_ACCESS_NETWORK_STATE};

    /* SDK instance object */
    FacePassHandler mFacePassHandler;

    /* camera instance */
    private CameraManager manager;
    private CameraManager mIRCameraManager;

    /* Display face position angle information */
    private TextView faceBeginTextView;

    /* show faceId */
    private TextView faceEndTextView;

    /* Camera preview interface */
    private CameraPreview cameraView;
    private CameraPreview mIRCameraView;

    private boolean isLocalGroupExist = false;

    /* Circle the face in the preview interface */
    private FaceView faceView;

    private ScrollView scrollView;

    /* Whether the camera uses the front camera */
    private static boolean cameraFacingFront = true;
    private int cameraRotation;

    private static final int cameraWidth = 1280;
    private static final int cameraHeight = 720;

    private int mSecretNumber = 0;
    private static final long CLICK_INTERVAL = 600;
    private long mLastClickTime;


    private int heightPixels;
    private int widthPixels;

    int screenState = 0;// 0 horizontal 1 vertical

    Button visible;
    LinearLayout ll;
    FrameLayout frameLayout;
    private int buttonFlag = 0;
    private Button settingButton;
    private boolean ageGenderEnabledGlobal;

    /*Toast*/
    private Toast mRecoToast;

    /*DetectResult queue*/
    public class RecognizeData {
        public byte[] message;
        public FacePassTrackOptions[] trackOpt;

        public RecognizeData(byte[] message, FacePassTrackOptions[] opt) {
            this.message = message;
            this.trackOpt = opt;
        }
    }

    ArrayBlockingQueue<RecognizeData> mRecognizeDataQueue;
    ArrayBlockingQueue<CameraPreviewData> mFeedFrameQueue;
    /*recognize thread*/
    RecognizeThread mRecognizeThread;
    FeedFrameThread mFeedFrameThread;


    /* Group Synchronization */
    private ImageView mSyncGroupBtn;
    private AlertDialog mSyncGroupDialog;

    private ImageView mFaceOperationBtn;
    /* image cache */
    private FaceImageCache mImageCache;

    private Handler mAndroidHandler;

    private Button mSDKModeBtn;

    String path = "";

    //take photo
    private static final int CAPTURE_IMAGE_REQUEST = 123;
    private File photoFile;
    private String mCurrentPhotoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mImageCache = new FaceImageCache();
        mRecognizeDataQueue = new ArrayBlockingQueue<>(5);
        mFeedFrameQueue = new ArrayBlockingQueue<>(1);
        initAndroidHandler();

        /* Initialization interface */
        initView();
        /* Permissions required for the application process */
        if (!hasPermission()) {
            requestPermission();
        } else {
            try {
                initFacePassSDK();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        initFaceHandler();

        mRecognizeThread = new RecognizeThread();
        mRecognizeThread.start();
        mFeedFrameThread = new FeedFrameThread();
        mFeedFrameThread.start();
    }

    private void initAndroidHandler() {
        mAndroidHandler = new Handler();
    }

    private void singleCertification(Context mContext) throws IOException {
        String cert = FileUtil.readExternal(CERT_PATH).trim();
        if (TextUtils.isEmpty(cert)) {
            Log.d("mcvsafe", "cert is null");
            return;
        }
        final AuthApplyResponse[] resp = {new AuthApplyResponse()};
        FacePassHandler.authDevice(mContext.getApplicationContext(), cert, "", new AuthApi.AuthDeviceCallBack() {
            @Override
            public void GetAuthDeviceResult(AuthApplyResponse result) {
                resp[0] = result;
                if (resp[0].errorCode == ErrorCodeConfig.AUTH_SUCCESS) {
                    try {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.d("mcvsafe", "Apply update: OK");
                            }
                        });
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                    }
                } else {
                    try {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.d("mcvsafe", "Apply update: error. error code is: " + resp[0].errorCode + " error message: " + resp[0].errorMessage);
                            }
                        });
                    } catch (Throwable throwable) {
                        throwable.printStackTrace();
                    }
                }
            }
        });
    }

    private void initFacePassSDK() throws IOException {
        Context mContext = getApplicationContext();
        FacePassHandler.initSDK(mContext);
        if (authType == FacePassAuthType.FASSPASS_AUTH_MCVFACE) {
            // face++authorized
            FacePassHandler.authPrepare(getApplicationContext());
            FacePassHandler.getAuth(authIP, apiKey, apiSecret, true);
            //Toast.makeText(this, "Auth Type FacePass Auth MCVFace", Toast.LENGTH_LONG).show();
        } else if (authType == FacePassAuthType.FACEPASS_AUTH_MCVSAFE) {
            // Gemalto Authorized Interface
            boolean auth_status = FacePassHandler.authCheck();
            if (!auth_status) {
                singleCertification(mContext);
                auth_status = FacePassHandler.authCheck();
            }

            if (!auth_status) {
                Log.d("mcvsafe", "Authentication result : failed.");
                //Toast.makeText(this, "Authentication result : failed.", Toast.LENGTH_LONG).show();
                // Authorization is unsuccessful, handle it according to business needs
                // ...
                return;
            }
        } else {
            //Toast.makeText(this, "FacePassDemo have no auth", Toast.LENGTH_LONG).show();
            Log.d("FacePassDemo", "have no auth.");
            return;
        }

        Log.d("FacePassDemo", FacePassHandler.getVersion());
    }

    private void initFaceHandler() {
        new Thread() {
            @Override
            public void run() {
                while (true && !isFinishing()) {
                    while (FacePassHandler.isAvailable()) {
                        Log.d(DEBUG_TAG, "start to build FacePassHandler");
                        FacePassConfig config;
                        try {
                            /* Fill in the required model configuration */
                            config = new FacePassConfig();
                            config.poseBlurModel = FacePassModel.initModel(getApplicationContext().getAssets(), "attr.pose_blur.arm.190630.bin");

                            config.livenessModel = FacePassModel.initModel(getApplicationContext().getAssets(), "liveness.CPU.rgb.G.bin");
                            if (CamType == FacePassCameraType.FACEPASS_DUALCAM) {
                                config.rgbIrLivenessModel = FacePassModel.initModel(getApplicationContext().getAssets(), "liveness.CPU.rgbir.I.bin");
                                // Real and fake models on the same screen
                                config.rgbIrGaLivenessModel = FacePassModel.initModel(getApplicationContext().getAssets(), "liveness.CPU.rgbir.ga_case.A.bin");
                                // If you need to use the GPU model, load the following model files
                                config.livenessGPUCache = FacePassModel.initModel(getApplicationContext().getAssets(), "liveness.GPU.rgbir.I.cache");
                                config.rgbIrLivenessGpuModel = FacePassModel.initModel(getApplicationContext().getAssets(), "liveness.GPU.rgbir.I.bin");
                                config.rgbIrGaLivenessGpuModel = FacePassModel.initModel(getApplicationContext().getAssets(), "liveness.GPU.rgbir.ga_case.A.bin");
                            }

                            config.searchModel = FacePassModel.initModel(getApplicationContext().getAssets(), "feat2.arm.K.v1.0_1core.bin");

                            config.detectModel = FacePassModel.initModel(getApplicationContext().getAssets(), "detector.arm.G.bin");
                            config.detectRectModel = FacePassModel.initModel(getApplicationContext().getAssets(), "detector_rect.arm.G.bin");
                            config.landmarkModel = FacePassModel.initModel(getApplicationContext().getAssets(), "pf.lmk.arm.E.bin");

                            config.rcAttributeModel = FacePassModel.initModel(getApplicationContext().getAssets(), "attr.RC.arm.G.bin");
                            config.occlusionFilterModel = FacePassModel.initModel(getApplicationContext().getAssets(), "attr.occlusion.arm.20201209.bin");
                            //config.smileModel = FacePassModel.initModel(getApplicationContext().getAssets(), "attr.RC.arm.200815.bin");
                            //config.ageGenderModel = FacePassModel.initModel(getApplicationContext().getAssets(), "attr.age_gender.arm.190630.bin");

                            /* Send recognition threshold parameters */
                            config.rcAttributeAndOcclusionMode = 1;
                            config.searchThreshold = 65f;
                            config.livenessThreshold = 80f;
                            config.livenessGaThreshold = 85f;
                            if (CamType == FacePassCameraType.FACEPASS_DUALCAM) {
                                config.livenessEnabled = false;
                                config.rgbIrLivenessEnabled = true;      // Enable binocular living function (default CPU)
                                config.rgbIrLivenessGpuEnabled = true;   // Enable binocular living GPU function
                                config.rgbIrGaLivenessEnabled = true;    // Enable the function of real and fake people on the same screen (default CPU)
                                config.rgbIrGaLivenessGpuEnabled = true; // Enable the GPU function of real and fake people on the same screen
                            } else {
                                config.livenessEnabled = true;
                                config.rgbIrLivenessEnabled = false;
                            }

                            ageGenderEnabledGlobal = (config.ageGenderModel != null);

                            config.poseThreshold = new FacePassPose(35f, 35f, 35f);
                            config.blurThreshold = 0.8f;
                            config.lowBrightnessThreshold = 30f;
                            config.highBrightnessThreshold = 210f;
                            config.brightnessSTDThreshold = 80f;
                            config.faceMinThreshold = 60;
                            config.retryCount = 10;
                            config.smileEnabled = false;
                            config.maxFaceEnabled = true;
                            config.fileRootPath = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();

                            /* Create an SDK instance */
                            mFacePassHandler = new FacePassHandler(config);

                            /* Inbound Threshold Parameters */
                            FacePassConfig addFaceConfig = mFacePassHandler.getAddFaceConfig();
                            addFaceConfig.poseThreshold.pitch = 35f;
                            addFaceConfig.poseThreshold.roll = 35f;
                            addFaceConfig.poseThreshold.yaw = 35f;
                            addFaceConfig.blurThreshold = 0.7f;
                            addFaceConfig.lowBrightnessThreshold = 70f;
                            addFaceConfig.highBrightnessThreshold = 220f;
                            addFaceConfig.brightnessSTDThresholdLow = 14.14f;
                            addFaceConfig.brightnessSTDThreshold = 63.25f;
                            addFaceConfig.faceMinThreshold = 100;
                            addFaceConfig.rcAttributeAndOcclusionMode = 2;
                            mFacePassHandler.setAddFaceConfig(addFaceConfig);

                            //autoCreateGroup();
                            checkGroup();
                        } catch (FacePassException e) {
                            e.printStackTrace();
                            Log.d(DEBUG_TAG, "FacePassHandler is null");
                            return;
                        }
                        return;
                    }
                    try {
                        /* If the SDK initialization is not completed, you need to wait */
                        sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            }
        }.start();
    }

    @Override
    protected void onResume() {
        checkGroup();
        initToast();
        /* Turn on the camera */
        if (hasPermission()) {
            manager.open(getWindowManager(), false, cameraWidth, cameraHeight);
            if (CamType == FacePassCameraType.FACEPASS_DUALCAM) {
                mIRCameraManager.open(getWindowManager(), true, cameraWidth, cameraHeight);
            }
        }
        adaptFrameLayout();
        super.onResume();
    }

    private void addFace(String path) {
        if (mFacePassHandler == null) {
            toast("FacePassHandle is null ! ");
            return;
        }
        String imagePath = path;
        if (TextUtils.isEmpty(imagePath)) {
            toast("Please enter the correct image path！");
            return;
        }

        File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            toast("picture does not exist ！");
            return;
        }

        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);

        try {
            FacePassAddFaceResult result = mFacePassHandler.addFace(bitmap);
            if (result != null) {
                if (result.result == 0) {
                    Log.d("qujiaqi", "result:" + result
                            + ",bl:" + result.blur
                            + ",pp:" + result.pose.pitch
                            + ",pr:" + result.pose.roll
                            + ",py" + result.pose.yaw);
                    toast("add face successfully！");
                    //faceTokenBytes = result.faceToken;
                } else if (result.result == 1) {
                    toast("no face ！");
                } else {
                    toast("quality problem！");
                }
            }
        } catch (FacePassException e) {
            e.printStackTrace();
            toast(e.getMessage());
        }
    }

    private void autoCreateGroup() {
        if (mFacePassHandler == null) {
            toast("FacePassHandle is null ! ");
            return;
        }
        String groupName = "facepass";
        if (TextUtils.isEmpty(groupName)) {
            toast("please input group name ！");
            return;
        }
        boolean isSuccess = false;
        try {
            isSuccess = mFacePassHandler.createLocalGroup(groupName);
        } catch (FacePassException e) {
            e.printStackTrace();
        }
        toast("create group " + isSuccess);
        if (isSuccess && group_name.equals(groupName)) {
            isLocalGroupExist = true;
        }
    }

    private void checkGroup() {
        if (mFacePassHandler == null) {
            return;
        }
        try {
            String[] localGroups = mFacePassHandler.getLocalGroups();
            isLocalGroupExist = false;
            if (localGroups == null || localGroups.length == 0) {
                faceView.post(new Runnable() {
                    @Override
                    public void run() {
                        toast("please create " + group_name + " group");
                    }
                });
                return;
            }
            for (String group : localGroups) {
                if (group_name.equals(group)) {
                    isLocalGroupExist = true;
                }
            }
            if (!isLocalGroupExist) {
                faceView.post(new Runnable() {
                    @Override
                    public void run() {
                        toast("please create " + group_name + " group");
                    }
                });
            }
        } catch (FacePassException e) {
            e.printStackTrace();
        }
    }

    /* Camera callback function */
    @Override
    public void onPictureTaken(CameraPreviewData cameraPreviewData) {
        if (CamType == FacePassCameraType.FACEPASS_DUALCAM) {
            ComplexFrameHelper.addRgbFrame(cameraPreviewData);
        } else {
            mFeedFrameQueue.offer(cameraPreviewData);
        }
    }

    public class FeedFrameThread extends Thread {
        boolean isInterrupt;

        @Override
        public void run() {
            while (!isInterrupt) {
                if (mFacePassHandler == null) {
                    continue;
                }
                /* Convert the camera preview frame to the frame format required by the SDK algorithm FacePassImage */
                long startTime = System.currentTimeMillis();

                /* Send each frame of FacePassImage into the SDK algorithm and get the returned result */
                FacePassDetectionResult detectionResult = null;
                try {
                    if (CamType == FacePassCameraType.FACEPASS_DUALCAM) {
                        Pair<CameraPreviewData, CameraPreviewData> framePair;
                        try {
                            framePair = ComplexFrameHelper.takeComplexFrame();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            continue;
                        }
                        FacePassImage imageRGB = new FacePassImage(framePair.first.nv21Data, framePair.first.width, framePair.first.height, cameraRotation, FacePassImageType.NV21);
                        FacePassImage imageIR = new FacePassImage(framePair.second.nv21Data, framePair.second.width, framePair.second.height, cameraRotation, FacePassImageType.NV21);
                        detectionResult = mFacePassHandler.feedFrameRGBIR(imageRGB, imageIR);
                    } else {
                        CameraPreviewData cameraPreviewData = null;
                        try {
                            cameraPreviewData = mFeedFrameQueue.take();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            continue;
                        }
                        FacePassImage imageRGB = new FacePassImage(cameraPreviewData.nv21Data, cameraPreviewData.width, cameraPreviewData.height, cameraRotation, FacePassImageType.NV21);
                        detectionResult = mFacePassHandler.feedFrame(imageRGB);
                    }
                } catch (FacePassException e) {
                    e.printStackTrace();
                }

                if (detectionResult == null || detectionResult.faceList.length == 0) {
                    /* No face is detected in the current frame */
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            faceView.clear();
                            faceView.invalidate();
                        }
                    });
                } else {
                    /* Circle the recognized face in the preview interface, and display the face position and angle information on the top */
                    final FacePassFace[] bufferFaceList = detectionResult.faceList;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showFacePassFace(bufferFaceList);
                        }
                    });
                }

                if (SDK_MODE == FacePassSDKMode.MODE_OFFLINE) {
                    /*In offline mode, add the result of the recognized face and the message is not empty to the processing queue*/
                    if (detectionResult != null && detectionResult.message.length != 0) {
                        Log.d(DEBUG_TAG, "mRecognizeDataQueue.offer");
                        /*Attribute information of all detected face frames*/
                        for (int i = 0; i < detectionResult.faceList.length; ++i) {
                            Log.d(DEBUG_TAG, String.format("rc attribute faceList hairType: 0x%x beardType: 0x%x hatType: 0x%x respiratorType: 0x%x glassesType: 0x%x skinColorType: 0x%x",
                                    detectionResult.faceList[i].rcAttr.hairType.ordinal(),
                                    detectionResult.faceList[i].rcAttr.beardType.ordinal(),
                                    detectionResult.faceList[i].rcAttr.hatType.ordinal(),
                                    detectionResult.faceList[i].rcAttr.respiratorType.ordinal(),
                                    detectionResult.faceList[i].rcAttr.glassesType.ordinal(),
                                    detectionResult.faceList[i].rcAttr.skinColorType.ordinal()));
                        }
                        Log.d(DEBUG_TAG, "--------------------------------------------------------------------------------------------------------------------------------------------------");
                        /*Send the attribute information of the recognized face frame*/
                        FacePassTrackOptions[] trackOpts = new FacePassTrackOptions[detectionResult.images.length];
                        for (int i = 0; i < detectionResult.images.length; ++i) {
                            if (detectionResult.images[i].rcAttr.respiratorType != FacePassRCAttribute.FacePassRespiratorType.INVALID
                                    && detectionResult.images[i].rcAttr.respiratorType != FacePassRCAttribute.FacePassRespiratorType.NO_RESPIRATOR) {
                                float searchThreshold = 60f;
                                float livenessThreshold = 80f; // -1.0f will not change the liveness threshold
                                float livenessGaThreshold = 85f;
                                float smallsearchThreshold = -1.0f; // -1.0f will not change the smallsearch threshold
                                trackOpts[i] = new FacePassTrackOptions(detectionResult.images[i].trackId, searchThreshold, livenessThreshold, livenessGaThreshold, smallsearchThreshold);
                            }
                            Log.d(DEBUG_TAG, String.format("rc attribute in FacePassImage, hairType: 0x%x beardType: 0x%x hatType: 0x%x respiratorType: 0x%x glassesType: 0x%x skinColorType: 0x%x",
                                    detectionResult.images[i].rcAttr.hairType.ordinal(),
                                    detectionResult.images[i].rcAttr.beardType.ordinal(),
                                    detectionResult.images[i].rcAttr.hatType.ordinal(),
                                    detectionResult.images[i].rcAttr.respiratorType.ordinal(),
                                    detectionResult.images[i].rcAttr.glassesType.ordinal(),
                                    detectionResult.images[i].rcAttr.skinColorType.ordinal()));
                        }
                        RecognizeData mRecData = new RecognizeData(detectionResult.message, trackOpts);
                        mRecognizeDataQueue.offer(mRecData);
                    }
                }
                long endTime = System.currentTimeMillis(); //End Time
                long runTime = endTime - startTime;
                for (int i = 0; i < detectionResult.faceList.length; ++i) {
                    Log.i("DEBUG_TAG", "rect[" + i + "] = (" + detectionResult.faceList[i].rect.left + ", " + detectionResult.faceList[i].rect.top + ", " + detectionResult.faceList[i].rect.right + ", " + detectionResult.faceList[i].rect.bottom);
                }
                Log.i("]time", String.format("feedfream %d ms", runTime));
            }
        }

        @Override
        public void interrupt() {
            isInterrupt = true;
            super.interrupt();
        }
    }

    int findidx(FacePassAgeGenderResult[] results, long trackId) {
        int result = -1;
        if (results == null) {
            return result;
        }
        for (int i = 0; i < results.length; ++i) {
            if (results[i].trackId == trackId) {
                return i;
            }
        }
        return result;
    }

    public class RecognizeThread extends Thread {

        boolean isInterrupt;

        @Override
        public void run() {
            while (!isInterrupt) {
                try {
                    RecognizeData recognizeData = mRecognizeDataQueue.take();
                    FacePassAgeGenderResult[] ageGenderResult = null;

                    if (isLocalGroupExist) {
                        Log.d(DEBUG_TAG, "RecognizeData >>>>");

                        FacePassRecognitionResult[][] recognizeResultArray = mFacePassHandler.recognize(group_name, recognizeData.message, 1, recognizeData.trackOpt);
                        if (recognizeResultArray != null && recognizeResultArray.length > 0) {
                            for (FacePassRecognitionResult[] recognizeResult : recognizeResultArray) {
                                if (recognizeResult != null && recognizeResult.length > 0) {
                                    for (FacePassRecognitionResult result : recognizeResult) {
                                        if (result.faceToken != null || new String(result.faceToken) != null || new String(result.faceToken) != "") {
                                            String faceToken = new String(result.faceToken);
                                            if (FacePassRecognitionState.RECOGNITION_PASS == result.recognitionState) {
                                                getFaceImageByFaceToken(result.trackId, faceToken);
                                            }
                                            int idx = findidx(ageGenderResult, result.trackId);
                                            if (idx == -1) {
                                                showRecognizeResult(result.trackId, result.detail.searchScore, result.detail.livenessScore, !TextUtils.isEmpty(faceToken));
                                            } else {
                                                showRecognizeResult(result.trackId, result.detail.searchScore, result.detail.livenessScore, !TextUtils.isEmpty(faceToken), ageGenderResult[idx].age, ageGenderResult[idx].gender);
                                            }

                                            Log.d(DEBUG_TAG, String.format("recognize trackid: %d, searchScore: %f  searchThreshold: %f, hairType: 0x%x beardType: 0x%x hatType: 0x%x respiratorType: 0x%x glassesType: 0x%x skinColorType: 0x%x",
                                                    result.trackId,
                                                    result.detail.searchScore,
                                                    result.detail.searchThreshold,
                                                    result.detail.rcAttr.hairType.ordinal(),
                                                    result.detail.rcAttr.beardType.ordinal(),
                                                    result.detail.rcAttr.hatType.ordinal(),
                                                    result.detail.rcAttr.respiratorType.ordinal(),
                                                    result.detail.rcAttr.glassesType.ordinal(),
                                                    result.detail.rcAttr.skinColorType.ordinal()));
                                        } else {
                                            Toast.makeText(getApplicationContext(), "Please Register For Face Recognition", Toast.LENGTH_LONG).show();
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (FacePassException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        public void interrupt() {
            isInterrupt = true;
            super.interrupt();
        }
    }

    private void showRecognizeResult(final long trackId, final float searchScore, final float livenessScore, final boolean isRecognizeOK) {
        mAndroidHandler.post(new Runnable() {
            @Override
            public void run() {
                faceEndTextView.append("ID = " + trackId + (isRecognizeOK ? "Successful recognition" : "recognition failed") + "\n");
                faceEndTextView.append("recognition score = " + searchScore + "\n");
                faceEndTextView.append("living body fraction = " + livenessScore + "\n");
                scrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    private void showRecognizeResult(final long trackId, final float searchScore, final float livenessScore, final boolean isRecognizeOK, final float age, final int gender) {
        mAndroidHandler.post(new Runnable() {
            @Override
            public void run() {
                faceEndTextView.append("ID = " + trackId + (isRecognizeOK ? "Successful recognition" : "recognition failed") + "\n");
                faceEndTextView.append("recognition score = " + searchScore + "\n");
                faceEndTextView.append("living body fraction = " + livenessScore + "\n");
                faceEndTextView.append("age = " + age + "\n");
                if (gender == 0) {
                    faceEndTextView.append("gender = " + "male" + "\n");
                } else if (gender == 1) {
                    faceEndTextView.append("gender = " + "female" + "\n");
                } else {
                    faceEndTextView.append("gender = " + "unknown" + "\n");
                }
                scrollView.fullScroll(ScrollView.FOCUS_DOWN);
            }
        });
    }

    /* Judging whether the program has the required permissions android22 and above need to apply for permissions */
    private boolean hasPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(PERMISSION_READ_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(PERMISSION_WRITE_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(PERMISSION_INTERNET) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(PERMISSION_ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    /* request program permissions */
    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(Permission, PERMISSIONS_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST) {
            boolean granted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED)
                    granted = false;
            }
            if (!granted) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    if (!shouldShowRequestPermissionRationale(PERMISSION_CAMERA)
                            || !shouldShowRequestPermissionRationale(PERMISSION_READ_STORAGE)
                            || !shouldShowRequestPermissionRationale(PERMISSION_WRITE_STORAGE)
                            || !shouldShowRequestPermissionRationale(PERMISSION_INTERNET)
                            || !shouldShowRequestPermissionRationale(PERMISSION_ACCESS_NETWORK_STATE)) {
                    }
            } else {
                try {
                    initFacePassSDK();
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void adaptFrameLayout() {
        SettingVar.isButtonInvisible = false;
        SettingVar.iscameraNeedConfig = false;
    }

    private void initToast() {
        SettingVar.isButtonInvisible = false;
    }

    private void initView() {
        int windowRotation = ((WindowManager) (getApplicationContext().getSystemService(Context.WINDOW_SERVICE))).getDefaultDisplay().getRotation() * 90;
        if (windowRotation == 0) {
            cameraRotation = FacePassImageRotation.DEG90;
        } else if (windowRotation == 90) {
            cameraRotation = FacePassImageRotation.DEG0;
        } else if (windowRotation == 270) {
            cameraRotation = FacePassImageRotation.DEG180;
        } else {
            cameraRotation = FacePassImageRotation.DEG270;
        }
        Log.i(DEBUG_TAG, "Rotation: cameraRation: " + cameraRotation);
        cameraFacingFront = true;
        SharedPreferences preferences = getSharedPreferences(SettingVar.SharedPrefrence, Context.MODE_PRIVATE);
        SettingVar.isSettingAvailable = preferences.getBoolean("isSettingAvailable", SettingVar.isSettingAvailable);
        SettingVar.isCross = preferences.getBoolean("isCross", SettingVar.isCross);
        SettingVar.faceRotation = preferences.getInt("faceRotation", SettingVar.faceRotation);
        SettingVar.cameraPreviewRotation = preferences.getInt("cameraPreviewRotation", SettingVar.cameraPreviewRotation);
        SettingVar.cameraFacingFront = preferences.getBoolean("cameraFacingFront", SettingVar.cameraFacingFront);
        if (SettingVar.isSettingAvailable) {
            cameraRotation = SettingVar.faceRotation;
            cameraFacingFront = SettingVar.cameraFacingFront;
        }

        Log.i(DEBUG_TAG, "Rotation: screenRotation: " + windowRotation);
        Log.i(DEBUG_TAG, "Rotation: faceRotation: " + SettingVar.faceRotation);
        Log.i(DEBUG_TAG, "Rotation: new cameraRation: " + cameraRotation);
        final int mCurrentOrientation = getResources().getConfiguration().orientation;

        if (mCurrentOrientation == Configuration.ORIENTATION_PORTRAIT) {
            screenState = 1;
        } else if (mCurrentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            screenState = 0;
        }
        setContentView(R.layout.activity_main);

        mSyncGroupBtn = (ImageView) findViewById(R.id.btn_group_name);
        mSyncGroupBtn.setOnClickListener(this);

        mFaceOperationBtn = (ImageView) findViewById(R.id.btn_face_operation);
        mFaceOperationBtn.setOnClickListener(this);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        heightPixels = displayMetrics.heightPixels;
        widthPixels = displayMetrics.widthPixels;
        SettingVar.mHeight = heightPixels;
        SettingVar.mWidth = widthPixels;
        scrollView = (ScrollView) findViewById(R.id.scrollView);
        AssetManager mgr = getAssets();
        Typeface tf = Typeface.createFromAsset(mgr, "fonts/Univers LT 57 Condensed.ttf");
        /* Initialization interface */
        faceEndTextView = (TextView) this.findViewById(R.id.tv_meg2);
        faceEndTextView.setTypeface(tf);
        faceView = (FaceView) this.findViewById(R.id.fcview);
        settingButton = (Button) this.findViewById(R.id.settingid);
        settingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                long curTime = System.currentTimeMillis();
                long durTime = curTime - mLastClickTime;
                mLastClickTime = curTime;
                if (durTime < CLICK_INTERVAL) {
                    ++mSecretNumber;
                    if (mSecretNumber == 5) {
                        Intent intent = new Intent(MainActivity.this, SettingActivity.class);
                        startActivity(intent);
                        MainActivity.this.finish();
                    }
                } else {
                    mSecretNumber = 0;
                }
            }
        });
        SettingVar.cameraSettingOk = false;
        ll = (LinearLayout) this.findViewById(R.id.ll);
        ll.getBackground().setAlpha(100);
        visible = (Button) this.findViewById(R.id.visible);
        visible.setBackgroundResource(R.drawable.debug);
        visible.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (buttonFlag == 0) {
                    ll.setVisibility(View.VISIBLE);
                    if (mCurrentOrientation == Configuration.ORIENTATION_PORTRAIT) {
                        visible.setBackgroundResource(R.drawable.down);
                    } else if (mCurrentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                        visible.setBackgroundResource(R.drawable.right);
                    }
                    buttonFlag = 1;
                } else if (buttonFlag == 1) {
                    buttonFlag = 0;
                    if (SettingVar.isButtonInvisible)
                        ll.setVisibility(View.INVISIBLE);
                    else
                        ll.setVisibility(View.GONE);
                    visible.setBackgroundResource(R.drawable.debug);
                }

            }
        });
        manager = new CameraManager();
        cameraView = (CameraPreview) findViewById(R.id.preview);
        manager.setPreviewDisplay(cameraView);
        frameLayout = (FrameLayout) findViewById(R.id.frame);
        /* Register camera callback function */
        manager.setListener(this);

        if (CamType == FacePassCameraType.FACEPASS_DUALCAM) {
            mIRCameraManager = new CameraManager();
            mIRCameraView = (CameraPreview) findViewById(R.id.preview2);
            mIRCameraManager.setPreviewDisplay(mIRCameraView);
            mIRCameraManager.setListener(new CameraManager.CameraListener() {
                @Override
                public void onPictureTaken(CameraPreviewData cameraPreviewData) {
                    ComplexFrameHelper.addIRFrame(cameraPreviewData);
                }
            });
        }

        mSDKModeBtn = (Button) findViewById(R.id.btn_mode_switch);
        mSDKModeBtn.setText(SDK_MODE.toString() + "\n" + CamType.toString());
        mSDKModeBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });

    }


    @Override
    protected void onStop() {
        SettingVar.isButtonInvisible = false;
        mRecognizeDataQueue.clear();
        if (manager != null) {
            manager.release();
        }
        if (mIRCameraManager != null) {
            mIRCameraManager.release();
        }
        super.onStop();
    }

    @Override
    protected void onRestart() {
        faceView.clear();
        faceView.invalidate();
        super.onRestart();
    }

    @Override
    protected void onDestroy() {
        mRecognizeThread.isInterrupt = true;
        mFeedFrameThread.isInterrupt = true;

        mRecognizeThread.interrupt();
        mFeedFrameThread.interrupt();

        if (manager != null) {
            manager.release();
        }
        if (mIRCameraManager != null) {
            mIRCameraManager.release();
        }
        if (mAndroidHandler != null) {
            mAndroidHandler.removeCallbacksAndMessages(null);
        }

        if (mFacePassHandler != null) {
            mFacePassHandler.release();
        }
        super.onDestroy();
    }


    private void showFacePassFace(FacePassFace[] detectResult) {
        faceView.clear();
        for (FacePassFace face : detectResult) {
            Log.d("facefacelist", "width " + (face.rect.right - face.rect.left) + " height " + (face.rect.bottom - face.rect.top));
            Log.d("facefacelist", "smile " + face.smile);
            boolean mirror = cameraFacingFront; /* Mirror is true for the front camera */
            StringBuilder faceIdString = new StringBuilder();
            faceIdString.append("ID = ").append(face.trackId);
            SpannableString faceViewString = new SpannableString(faceIdString);
            faceViewString.setSpan(new TypefaceSpan("fonts/kai"), 0, faceViewString.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            StringBuilder faceRollString = new StringBuilder();
            faceRollString.append("Roll: ").append((int) face.pose.roll).append("°");
            StringBuilder facePitchString = new StringBuilder();
            facePitchString.append("Pose Pitch: ").append((int) face.pose.pitch).append("°");
            StringBuilder faceYawString = new StringBuilder();
            faceYawString.append("Yaw: ").append((int) face.pose.yaw).append("°");
            StringBuilder faceBlurString = new StringBuilder();
            faceBlurString.append("Blur: ").append(face.blur);
            StringBuilder smileString = new StringBuilder();
            smileString.append("Smile: ").append(String.format("%.6f", face.smile));
            Matrix mat = new Matrix();
            int w = cameraView.getMeasuredWidth();
            int h = cameraView.getMeasuredHeight();

            int cameraHeight = manager.getCameraHeight();
            int cameraWidth = manager.getCameraWidth();

            float left = 0;
            float top = 0;
            float right = 0;
            float bottom = 0;
            switch (cameraRotation) {
                case 0:
                    left = face.rect.left;
                    top = face.rect.top;
                    right = face.rect.right;
                    bottom = face.rect.bottom;
                    mat.setScale(mirror ? -1 : 1, 1);
                    mat.postTranslate(mirror ? (float) cameraWidth : 0f, 0f);
                    mat.postScale((float) w / (float) cameraWidth, (float) h / (float) cameraHeight);
                    break;
                case 90:
                    mat.setScale(mirror ? -1 : 1, 1);
                    mat.postTranslate(mirror ? (float) cameraHeight : 0f, 0f);
                    mat.postScale((float) w / (float) cameraHeight, (float) h / (float) cameraWidth);
                    left = face.rect.top;
                    top = cameraWidth - face.rect.right;
                    right = face.rect.bottom;
                    bottom = cameraWidth - face.rect.left;
                    break;
                case 180:
                    mat.setScale(1, mirror ? -1 : 1);
                    mat.postTranslate(0f, mirror ? (float) cameraHeight : 0f);
                    mat.postScale((float) w / (float) cameraWidth, (float) h / (float) cameraHeight);
                    left = face.rect.right;
                    top = face.rect.bottom;
                    right = face.rect.left;
                    bottom = face.rect.top;
                    break;
                case 270:
                    mat.setScale(mirror ? -1 : 1, 1);
                    mat.postTranslate(mirror ? (float) cameraHeight : 0f, 0f);
                    mat.postScale((float) w / (float) cameraHeight, (float) h / (float) cameraWidth);
                    left = cameraHeight - face.rect.bottom;
                    top = face.rect.left;
                    right = cameraHeight - face.rect.top;
                    bottom = face.rect.right;
            }

            RectF drect = new RectF();
            RectF srect = new RectF(left, top, right, bottom);

            mat.mapRect(drect, srect);
            faceView.addRect(drect);
            faceView.addId(faceIdString.toString());
            faceView.addRoll(faceRollString.toString());
            faceView.addPitch(facePitchString.toString());
            faceView.addYaw(faceYawString.toString());
            faceView.addBlur(faceBlurString.toString());
            faceView.addSmile(smileString.toString());
        }
        faceView.invalidate();
    }

    public void showToast(CharSequence text, int duration, boolean isSuccess, Bitmap bitmap) {
        LayoutInflater inflater = getLayoutInflater();
        View toastView = inflater.inflate(R.layout.toast, null);
        LinearLayout toastLLayout = (LinearLayout) toastView.findViewById(R.id.toastll);
        if (toastLLayout == null) {
            return;
        }
        toastLLayout.getBackground().setAlpha(100);
        ImageView logo = (ImageView) toastView.findViewById(R.id.logo);
        ImageView imageView = (ImageView) toastView.findViewById(R.id.toastImageView);
        TextView idTextView = (TextView) toastView.findViewById(R.id.toastTextView);
        TextView stateView = (TextView) toastView.findViewById(R.id.toastState);
        SpannableString s;
        if (isSuccess) {
            s = new SpannableString("Verification successful");
            imageView.setImageResource(R.drawable.success);
        } else {
            logo.setVisibility(View.GONE);
            s = new SpannableString("Verification failed");
            imageView.setImageResource(R.drawable.fail);
        }

        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        }
        stateView.setText(s);
        idTextView.setText(text);

        if (mRecoToast == null) {
            mRecoToast = new Toast(getApplicationContext());
            mRecoToast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
        }
        mRecoToast.setDuration(duration);
        mRecoToast.setView(toastView);

        mRecoToast.show();
    }

    private static final int REQUEST_CODE_CHOOSE_PICK = 1;

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_group_name:
                showSyncGroupDialog();
                break;
            case R.id.btn_face_operation:
                showFaceSettingDialog();
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {

            case CAPTURE_IMAGE_REQUEST:
                if (resultCode == RESULT_OK) {
                    if (photoFile.getAbsolutePath() != null) {
                        Bitmap myBitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
                        showTakeImageDialog(myBitmap);
                    } else {

                    }
                } else {
                    Toast.makeText(this, "Request cancelled or something went wrong.", Toast.LENGTH_LONG).show();
                }
                //Read the address after selecting a photo from the album
            case REQUEST_CODE_CHOOSE_PICK:
                if (resultCode == RESULT_OK && data != null) {
                    if (data.getData() != null) {

                        Uri uri = data.getData();
                        String[] pojo = {MediaStore.Images.Media.DATA};
                        Cursor cursor = getContentResolver().query(uri, pojo, null, null, null);
                        //Cursor cursor = cursorLoader.loadInBackground();
                        if (cursor != null) {
                            cursor.moveToFirst();
                            path = cursor.getString(cursor.getColumnIndexOrThrow(pojo[0]));
                        }

                        if (!TextUtils.isEmpty(path) && "file".equalsIgnoreCase(uri.getScheme())) {
                            path = uri.getPath();
                        }
                        if (TextUtils.isEmpty(path)) {
                            try {
                                path = FileUtil.getPath(getApplicationContext(), uri);
                            } catch (Exception e) {
                            }
                        }
                        if (TextUtils.isEmpty(path)) {
                            toast("Image selection failed");
                            return;
                        }
                        if (!TextUtils.isEmpty(path) && mFaceOperationDialog != null && mFaceOperationDialog.isShowing()) {
                            EditText imagePathEdt = (EditText) mFaceOperationDialog.findViewById(R.id.et_face_image_path);
                            imagePathEdt.setText(path);
                            //addFace(path);
                        }
                    } else {

                    }
                }
                break;
        }
    }

    private void getFaceImageByFaceToken(final long trackId, String faceToken) {
        if (TextUtils.isEmpty(faceToken)) {
            return;
        }

        try {
            final Bitmap bitmap = mFacePassHandler.getFaceImage(faceToken.getBytes());
            mAndroidHandler.post(new Runnable() {
                @Override
                public void run() {
                    Log.i(DEBUG_TAG, "getFaceImageByFaceToken cache is null");
                    showToast("ID = " + trackId, Toast.LENGTH_SHORT, true, bitmap);
                }
            });
            if (bitmap != null) {
                return;
            }
        } catch (FacePassException e) {
            e.printStackTrace();
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }


    /*Synchronize Groups*/
    private void showSyncGroupDialog() {

        if (mSyncGroupDialog != null && mSyncGroupDialog.isShowing()) {
            mSyncGroupDialog.hide();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        View view = LayoutInflater.from(this).inflate(R.layout.layout_dialog_sync_groups, null);

        final EditText groupNameEt = (EditText) view.findViewById(R.id.et_group_name);
        final TextView syncDataTv = (TextView) view.findViewById(R.id.tv_show_sync_data);

        Button obtainGroupsBtn = (Button) view.findViewById(R.id.btn_obtain_groups);
        Button createGroupBtn = (Button) view.findViewById(R.id.btn_submit);
        ImageView closeWindowIv = (ImageView) view.findViewById(R.id.iv_close);

        final ListView groupNameLv = (ListView) view.findViewById(R.id.lv_group_name);
        final ScrollView syncScrollView = (ScrollView) view.findViewById(R.id.sv_handle_sync_data);

        final GroupNameAdapter groupNameAdapter = new GroupNameAdapter();

        builder.setView(view);

        groupNameEt.setText("facepass");

        closeWindowIv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mSyncGroupDialog.dismiss();
            }
        });

        obtainGroupsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mFacePassHandler == null) {
                    toast("FacePassHandle is null ! ");
                    return;
                }
                try {
                    String[] groups = mFacePassHandler.getLocalGroups();
                    if (groups != null && groups.length > 0) {
                        List<String> data = Arrays.asList(groups);
                        syncScrollView.setVisibility(View.GONE);
                        groupNameLv.setVisibility(View.VISIBLE);
                        groupNameAdapter.setData(data);
                        groupNameLv.setAdapter(groupNameAdapter);
                    } else {
                        toast("groups is null !");
                    }
                } catch (FacePassException e) {
                    e.printStackTrace();
                }
            }
        });

        createGroupBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mFacePassHandler == null) {
                    toast("FacePassHandle is null ! ");
                    return;
                }
                String groupName = groupNameEt.getText().toString();
                if (TextUtils.isEmpty(groupName)) {
                    toast("please input group name ！");
                    return;
                }
                boolean isSuccess = false;
                try {
                    isSuccess = mFacePassHandler.createLocalGroup(groupName);
                } catch (FacePassException e) {
                    e.printStackTrace();
                }
                toast("create group " + isSuccess);
                if (isSuccess && group_name.equals(groupName)) {
                    isLocalGroupExist = true;
                }

            }
        });

        groupNameAdapter.setOnItemDeleteButtonClickListener(new GroupNameAdapter.ItemDeleteButtonClickListener() {
            @Override
            public void OnItemDeleteButtonClickListener(int position) {
                List<String> groupNames = groupNameAdapter.getData();
                if (groupNames == null) {
                    return;
                }
                if (mFacePassHandler == null) {
                    toast("FacePassHandle is null ! ");
                    return;
                }
                String groupName = groupNames.get(position);
                boolean isSuccess = false;
                try {
                    isSuccess = mFacePassHandler.deleteLocalGroup(groupName);
                } catch (FacePassException e) {
                    e.printStackTrace();
                }
                if (isSuccess) {
                    try {
                        String[] groups = mFacePassHandler.getLocalGroups();
                        if (group_name.equals(groupName)) {
                            isLocalGroupExist = false;
                        }
                        if (groups != null) {
                            groupNameAdapter.setData(Arrays.asList(groups));
                            groupNameAdapter.notifyDataSetChanged();
                        }
                        toast("successfully deleted!");
                    } catch (FacePassException e) {
                        e.printStackTrace();
                    }
                } else {
                    toast("failed to delete!");

                }
            }

        });

        mSyncGroupDialog = builder.create();

        WindowManager m = getWindowManager();
        Display d = m.getDefaultDisplay();  //To get the screen width and height

        WindowManager.LayoutParams attributes = mSyncGroupDialog.getWindow().getAttributes();
        attributes.height = d.getHeight();
        attributes.width = d.getWidth();
        mSyncGroupDialog.getWindow().setAttributes(attributes);

        mSyncGroupDialog.show();

    }

    private AlertDialog mFaceOperationDialog;
    private AlertDialog mFaceSettingDialog;
    private AlertDialog mTakeImageDialog;

    private void showTakeImageDialog(final Bitmap bitmap) {

        if (mTakeImageDialog != null && !mTakeImageDialog.isShowing()) {
            mTakeImageDialog.show();
            return;
        }
        if (mTakeImageDialog != null && mTakeImageDialog.isShowing()) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.layout_dialog_take_image, null);
        builder.setView(view);

        ImageView close = (ImageView) view.findViewById(R.id.iv_close);
        ImageView takenPhoto = (ImageView) view.findViewById(R.id.imageTakenByCamera);
        Button cancelBtn = (Button) view.findViewById(R.id.cancel_btn);
        Button okBtn = (Button) view.findViewById(R.id.ok_btn);

        if (bitmap != null) {
            takenPhoto.setImageBitmap(bitmap);
        }

        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTakeImageDialog.dismiss();
            }
        });

        cancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTakeImageDialog.dismiss();
            }
        });

        okBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTakeImageDialog.dismiss();
                showAddFaceDialog();
                Intent intentFromGallery = new Intent(Intent.ACTION_GET_CONTENT);
                intentFromGallery.setType("image/*"); // set file type
                intentFromGallery.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivityForResult(intentFromGallery, REQUEST_CODE_CHOOSE_PICK);
                } catch (ActivityNotFoundException e) {
                    toast("Please install photo album or file manager");
                }
            }
        });

        WindowManager m = getWindowManager();
        Display d = m.getDefaultDisplay();  //To get the screen width and height
        mTakeImageDialog = builder.create();
        WindowManager.LayoutParams attributes = mTakeImageDialog.getWindow().getAttributes();
        attributes.height = d.getHeight();
        attributes.width = d.getWidth();
        mTakeImageDialog.getWindow().setAttributes(attributes);
        mTakeImageDialog.show();
    }

    private void showFaceSettingDialog() {
        if (mFaceSettingDialog != null && !mFaceSettingDialog.isShowing()) {
            mFaceSettingDialog.show();
            return;
        }
        if (mFaceSettingDialog != null && mFaceSettingDialog.isShowing()) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.layout_dialog_face_setting, null);
        builder.setView(view);

        final TextView createGroup = (TextView) view.findViewById(R.id.create_group);
        final TextView registerTakePhoto = (TextView) view.findViewById(R.id.register_take_photo);
        final TextView registerImportPhoto = (TextView) view.findViewById(R.id.register_import_photo);

        ImageView closeIv = (ImageView) view.findViewById(R.id.iv_close);

        closeIv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mFaceSettingDialog.dismiss();
            }
        });

        createGroup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSyncGroupDialog();
            }
        });

        registerImportPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddFaceDialog();
                mFaceSettingDialog.dismiss();
            }
        });

        registerTakePhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mFaceSettingDialog.dismiss();
                captureImage();
            }
        });

        WindowManager m = getWindowManager();
        Display d = m.getDefaultDisplay();  //To get the screen width and height
        mFaceSettingDialog = builder.create();
        WindowManager.LayoutParams attributes = mFaceSettingDialog.getWindow().getAttributes();
        attributes.height = d.getHeight();
        attributes.width = d.getWidth();
        mFaceSettingDialog.getWindow().setAttributes(attributes);
        mFaceSettingDialog.show();

    }

    private void captureImage() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    0
            );
        } else {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                try {
                    photoFile = createImageFile();
                    if (photoFile != null) {
                        Uri photoURI = FileProvider.getUriForFile(
                                this,
                                "com.sawthandar.faceregonition.fileprovider",
                                photoFile
                        );
                        path = photoURI.getPath();
                        takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                        startActivityForResult(takePictureIntent, CAPTURE_IMAGE_REQUEST);
                    }
                } catch (Exception e) {
                    Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "Null", Toast.LENGTH_LONG).show();
            }
        }
    }

    File createImageFile() {
        String root = Environment.getExternalStorageDirectory().toString();
        File myDir = new File(root + "/DCIM/Camera");
        myDir.mkdirs();
        Random generator = new Random();
        int n = 10000;
        n = generator.nextInt(n);
        String fileName = "FILENAME-" + n + ".jpg";
        File file = new File(myDir, fileName);
        if (file.exists()) file.delete();
        try {
            FileOutputStream out = new FileOutputStream(file);
            out.flush();
            out.close();

            mCurrentPhotoPath = file.getAbsolutePath();

        } catch (Exception e) {
            e.printStackTrace();
        }

        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri contentUri = Uri.fromFile(file);
        mediaScanIntent.setData(contentUri);
        getApplicationContext().sendBroadcast(mediaScanIntent);
        return file;

        /*String state = Environment.getExternalStorageState();
        File filesDir;
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            filesDir = new File(Environment.getExternalStorageDirectory() + "/FacePass/Media","FacePass Images");
        } else {
            filesDir = new File(getExternalFilesDir(null),"Images");
        }

        if(!filesDir.exists()) filesDir.mkdirs();
        return new File(filesDir, "FacePass.jpg");*/

        /*File dir = new File( Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM), "Camera");

        String imageFileName = "JPEG_";
        //File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName, ".jpg", dir
        );

        mCurrentPhotoPath = image.getAbsolutePath();
        return image;*/
    }

    public Uri getImageUri(Context inContext, Bitmap inImage) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        String path = MediaStore.Images.Media.insertImage(inContext.getContentResolver(), inImage, "Title", null);
        return Uri.parse(path);
    }

    public String getRealPathFromURI(Uri uri) {
        Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        cursor.moveToFirst();
        int idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
        return cursor.getString(idx);
    }

    private void broadCastMedia(Context context, String path) {
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File file = new File(path);
        intent.setData(Uri.fromFile(file));
        context.sendBroadcast(intent);
    }

    private void showAddFaceDialog() {

        if (mFaceOperationDialog != null && !mFaceOperationDialog.isShowing()) {
            mFaceOperationDialog.show();
            return;
        }
        if (mFaceOperationDialog != null && mFaceOperationDialog.isShowing()) {
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.layout_dialog_face_operation, null);
        builder.setView(view);

        final EditText faceImagePathEt = (EditText) view.findViewById(R.id.et_face_image_path);
        final EditText faceTokenEt = (EditText) view.findViewById(R.id.et_face_token);
        final TextView groupNameEt = (TextView) view.findViewById(R.id.et_group_name);

        Button choosePictureBtn = (Button) view.findViewById(R.id.btn_choose_picture);
        Button addFaceBtn = (Button) view.findViewById(R.id.btn_add_face);
        Button getFaceImageBtn = (Button) view.findViewById(R.id.btn_get_face_image);
        Button deleteFaceBtn = (Button) view.findViewById(R.id.btn_delete_face);
        Button bindGroupFaceTokenBtn = (Button) view.findViewById(R.id.btn_bind_group);
        Button getGroupInfoBtn = (Button) view.findViewById(R.id.btn_get_group_info);

        ImageView closeIv = (ImageView) view.findViewById(R.id.iv_close);

        final ListView groupInfoLv = (ListView) view.findViewById(R.id.lv_group_info);

        final FaceTokenAdapter faceTokenAdapter = new FaceTokenAdapter();

        groupNameEt.setText(group_name);

        closeIv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mFaceOperationDialog.dismiss();
            }
        });

        /*if (path != null) {

            if (TextUtils.isEmpty(path)) {
                toast("Image uploading failed");
                return;
            }

            faceImagePathEt.setText(path);
        }*/

        choosePictureBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intentFromGallery = new Intent(Intent.ACTION_GET_CONTENT);
                intentFromGallery.setType("image/*"); // set file type
                intentFromGallery.addCategory(Intent.CATEGORY_OPENABLE);
                try {
                    startActivityForResult(intentFromGallery, REQUEST_CODE_CHOOSE_PICK);
                } catch (ActivityNotFoundException e) {
                    toast("Please install photo album or file manager");
                }
            }
        });

        addFaceBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (mFacePassHandler == null) {
                    toast("FacePassHandle is null ! ");
                    return;
                }

                String imagePath = faceImagePathEt.getText().toString();

                if (TextUtils.isEmpty(imagePath)) {
                    toast("Please enter the correct image path！");
                    return;
                }

                File imageFile = new File(imagePath);
                if (!imageFile.exists()) {
                    toast("picture does not exist ！");
                    return;
                }

                Bitmap bitmap = BitmapFactory.decodeFile(imagePath);

                try {
                    FacePassAddFaceResult result = mFacePassHandler.addFace(bitmap);
                    if (result != null) {
                        if (result.result == 0) {
                            Log.d("qujiaqi", "result:" + result
                                    + ",bl:" + result.blur
                                    + ",pp:" + result.pose.pitch
                                    + ",pr:" + result.pose.roll
                                    + ",py" + result.pose.yaw);
                            toast("add face successfully！");
                            faceTokenEt.setText(new String(result.faceToken));
                        } else if (result.result == 1) {
                            toast("no face ！");
                        } else {
                            toast("quality problem！");
                        }
                    }
                } catch (FacePassException e) {
                    e.printStackTrace();
                    toast(e.getMessage());
                }
            }
        });

        getFaceImageBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mFacePassHandler == null) {
                    toast("FacePassHandle is null ! ");
                    return;
                }
                try {
                    byte[] faceToken = faceTokenEt.getText().toString().getBytes();
                    Bitmap bmp = mFacePassHandler.getFaceImage(faceToken);
                    final ImageView iv = (ImageView) findViewById(R.id.imview);
                    iv.setImageBitmap(bmp);
                    iv.setVisibility(View.VISIBLE);
                    iv.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            iv.setVisibility(View.GONE);
                            iv.setImageBitmap(null);
                        }
                    }, 2000);
                    mFaceOperationDialog.dismiss();
                } catch (Exception e) {
                    e.printStackTrace();
                    toast(e.getMessage());
                }
            }
        });

        deleteFaceBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mFacePassHandler == null) {
                    toast("FacePassHandle is null ! ");
                    return;
                }
                boolean b = false;
                try {
                    byte[] faceToken = faceTokenEt.getText().toString().getBytes();
                    b = mFacePassHandler.deleteFace(faceToken);
                    if (b) {
                        String groupName = groupNameEt.getText().toString();
                        if (TextUtils.isEmpty(groupName)) {
                            toast("group name  is null ！");
                            return;
                        }
                        byte[][] faceTokens = mFacePassHandler.getLocalGroupInfo(groupName);
                        List<String> faceTokenList = new ArrayList<>();
                        if (faceTokens != null && faceTokens.length > 0) {
                            for (int j = 0; j < faceTokens.length; j++) {
                                if (faceTokens[j].length > 0) {
                                    faceTokenList.add(new String(faceTokens[j]));
                                }
                            }
                        }
                        faceTokenAdapter.setData(faceTokenList);
                        groupInfoLv.setAdapter(faceTokenAdapter);
                    }
                } catch (FacePassException e) {
                    e.printStackTrace();
                    toast(e.getMessage());
                }

                String result = b ? "success " : "failed";
                toast("delete face " + result);
                Log.d(DEBUG_TAG, "delete face  " + result);

            }
        });

        bindGroupFaceTokenBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mFacePassHandler == null) {
                    toast("FacePassHandle is null ! ");
                    return;
                }

                byte[] faceToken = faceTokenEt.getText().toString().getBytes();
                String groupName = groupNameEt.getText().toString();
                if (faceToken == null || faceToken.length == 0 || TextUtils.isEmpty(groupName)) {
                    toast("params error！");
                    return;
                }
                try {
                    boolean b = mFacePassHandler.bindGroup(groupName, faceToken);
                    String result = b ? "success " : "failed";
                    toast("bind  " + result);
                } catch (Exception e) {
                    e.printStackTrace();
                    toast(e.getMessage());
                }


            }
        });

        getGroupInfoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mFacePassHandler == null) {
                    toast("FacePassHandle is null ! ");
                    return;
                }
                String groupName = groupNameEt.getText().toString();
                if (TextUtils.isEmpty(groupName)) {
                    toast("group name  is null ！");
                    return;
                }
                try {
                    byte[][] faceTokens = mFacePassHandler.getLocalGroupInfo(groupName);
                    List<String> faceTokenList = new ArrayList<>();
                    if (faceTokens != null && faceTokens.length > 0) {
                        for (int j = 0; j < faceTokens.length; j++) {
                            if (faceTokens[j].length > 0) {
                                faceTokenList.add(new String(faceTokens[j]));
                            }
                        }

                    }
                    faceTokenAdapter.setData(faceTokenList);
                    groupInfoLv.setAdapter(faceTokenAdapter);
                } catch (Exception e) {
                    e.printStackTrace();
                    toast("get local group info error!");
                }

            }
        });

        faceTokenAdapter.setOnItemButtonClickListener(new FaceTokenAdapter.ItemButtonClickListener() {
            @Override
            public void onItemDeleteButtonClickListener(int position) {
                if (mFacePassHandler == null) {
                    toast("FacePassHandle is null ! ");
                    return;
                }

                if (mFacePassHandler == null) {
                    toast("FacePassHandle is null ! ");
                    return;
                }
                String groupName = groupNameEt.getText().toString();
                if (TextUtils.isEmpty(groupName)) {
                    toast("group name  is null ！");
                    return;
                }
                try {
                    byte[] faceToken = faceTokenAdapter.getData().get(position).getBytes();
                    boolean b = mFacePassHandler.deleteFace(faceToken);
                    String result = b ? "success " : "failed";
                    toast("delete face " + result);
                    if (b) {
                        byte[][] faceTokens = mFacePassHandler.getLocalGroupInfo(groupName);
                        List<String> faceTokenList = new ArrayList<>();
                        if (faceTokens != null && faceTokens.length > 0) {
                            for (int j = 0; j < faceTokens.length; j++) {
                                if (faceTokens[j].length > 0) {
                                    faceTokenList.add(new String(faceTokens[j]));
                                }
                            }

                        }
                        faceTokenAdapter.setData(faceTokenList);
                        groupInfoLv.setAdapter(faceTokenAdapter);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    toast(e.getMessage());
                }

            }

            @Override
            public void onItemUnbindButtonClickListener(int position) {
                if (mFacePassHandler == null) {
                    toast("FacePassHandle is null ! ");
                    return;
                }

                String groupName = groupNameEt.getText().toString();
                if (TextUtils.isEmpty(groupName)) {
                    toast("group name  is null ！");
                    return;
                }
                try {
                    byte[] faceToken = faceTokenAdapter.getData().get(position).getBytes();
                    boolean b = mFacePassHandler.unBindGroup(groupName, faceToken);
                    String result = b ? "success " : "failed";
                    toast("unbind " + result);
                    if (b) {
                        byte[][] faceTokens = mFacePassHandler.getLocalGroupInfo(groupName);
                        List<String> faceTokenList = new ArrayList<>();
                        if (faceTokens != null && faceTokens.length > 0) {
                            for (int j = 0; j < faceTokens.length; j++) {
                                if (faceTokens[j].length > 0) {
                                    faceTokenList.add(new String(faceTokens[j]));
                                }
                            }

                        }
                        faceTokenAdapter.setData(faceTokenList);
                        groupInfoLv.setAdapter(faceTokenAdapter);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    toast("unbind error!");
                }

            }
        });


        WindowManager m = getWindowManager();
        Display d = m.getDefaultDisplay();  //To get the screen width and height
        mFaceOperationDialog = builder.create();
        WindowManager.LayoutParams attributes = mFaceOperationDialog.getWindow().getAttributes();
        attributes.height = d.getHeight();
        attributes.width = d.getWidth();
        mFaceOperationDialog.getWindow().setAttributes(attributes);
        mFaceOperationDialog.show();
    }

    private void toast(String msg) {
        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
    }


    /**
     * Download image cache according to facetoken
     */
    private static class FaceImageCache implements ImageLoader.ImageCache {

        private static final int CACHE_SIZE = 6 * 1024 * 1024;

        LruCache<String, Bitmap> mCache;

        public FaceImageCache() {
            mCache = new LruCache<String, Bitmap>(CACHE_SIZE) {

                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getRowBytes() * value.getHeight();
                }
            };
        }

        @Override
        public Bitmap getBitmap(String url) {
            return mCache.get(url);
        }

        @Override
        public void putBitmap(String url, Bitmap bitmap) {
            mCache.put(url, bitmap);
        }
    }

    private class FacePassRequest extends Request<String> {

        HttpEntity entity;

        FacePassDetectionResult mFacePassDetectionResult;
        private final Response.Listener<String> mListener;

        public FacePassRequest(String url, FacePassDetectionResult detectionResult, Response.Listener<String> listener, Response.ErrorListener errorListener) {
            super(Method.POST, url, errorListener);
            mFacePassDetectionResult = detectionResult;
            mListener = listener;
        }

        @Override
        protected Response<String> parseNetworkResponse(NetworkResponse response) {
            String parsed;
            try {
                parsed = new String(response.data, HttpHeaderParser.parseCharset(response.headers));
            } catch (UnsupportedEncodingException e) {
                parsed = new String(response.data);
            }
            return Response.success(parsed, HttpHeaderParser.parseCacheHeaders(response));
        }

        @Override
        protected void deliverResponse(String response) {
            mListener.onResponse(response);
        }

        @Override
        public String getBodyContentType() {
            return entity.getContentType().getValue();
        }

        @Override
        public byte[] getBody() throws AuthFailureError {
            MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
//        beginRecogIdArrayList.clear();

            for (FacePassImage passImage : mFacePassDetectionResult.images) {
                /* Convert the face image into a jpg format image for uploading */
                YuvImage img = new YuvImage(passImage.image, ImageFormat.NV21, passImage.width, passImage.height, null);
                Rect rect = new Rect(0, 0, passImage.width, passImage.height);
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                img.compressToJpeg(rect, 95, os);
                byte[] tmp = os.toByteArray();
                ByteArrayBody bab = new ByteArrayBody(tmp, passImage.trackId + ".jpg");
//            beginRecogIdArrayList.add(passImage.trackId);
                entityBuilder.addPart("image_" + passImage.trackId, bab);
            }
            StringBody sbody = null;
            try {
                sbody = new StringBody(MainActivity.group_name, ContentType.TEXT_PLAIN.withCharset(CharsetUtils.get("UTF-8")));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            entityBuilder.addPart("group_name", sbody);
            StringBody data = null;
            try {
                data = new StringBody(new String(mFacePassDetectionResult.message), ContentType.TEXT_PLAIN.withCharset(CharsetUtils.get("UTF-8")));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            entityBuilder.addPart("face_data", data);
            entity = entityBuilder.build();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                entity.writeTo(bos);
            } catch (IOException e) {
                VolleyLog.e("IOException writing to ByteArrayOutputStream");
            }
            byte[] result = bos.toByteArray();
            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return result;
        }
    }
}
