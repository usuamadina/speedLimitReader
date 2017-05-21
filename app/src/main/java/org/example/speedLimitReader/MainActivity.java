package org.example.speedLimitReader;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.InstallCallbackInterface;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;


public class MainActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2, LoaderCallbackInterface {

    private static final int SOLICITUD_PERMISO_CAMARA = 1;
    private static final int SOLICITUD_PERMISO_ALMACENAMIENTO = 2;
    private static final String TAG = "MainActivity";
    private CameraBridgeViewBase cameraView;
    private View view;
    private Bundle bundle;

    private int cameraIndex; // 0--> back camera; 1--> front camera
    private int cam_width = 320;// image resolution
    private int cam_height = 240;
    private static final String STATE_CAMERA_INDEX = "cameraIndex";
    private int inputType = 0; // 0 -->camera 1--> file1 2-->file2
    private Mat imageResource_, input, output;
    private boolean reloadResource;
    private boolean saveNextImage = false;
    private Processor processor;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e("onCreate", "oncreate");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        view = findViewById(R.id.content_main);
        Log.d("onCreate", "VISTA CREADA");
        cameraView = (CameraBridgeViewBase) findViewById(R.id.camera_view);
        if (savedInstanceState != null) {
            cameraIndex = savedInstanceState.getInt(STATE_CAMERA_INDEX, 0);
        } else {
            cameraIndex = CameraBridgeViewBase.CAMERA_ID_BACK;
        }
        cameraView.setCameraIndex(cameraIndex);
        cameraView.setCvCameraViewListener(this);
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
//            cameraSettings(savedInstanceState);
//            Log.e("onCreate", "tengo permisos");
//
//
//        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
//            Snackbar.make(view, "Se necesita permiso para usar la cámara.", Snackbar.LENGTH_INDEFINITE).setAction("OK", new View.OnClickListener() {
//                @Override
//                public void onClick(View view) {
//                    Log.d("onCreate","permisos rationale");
//                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, SOLICITUD_PERMISO_CAMARA);
//                }
//            }).show();
//        } else {
//            Log.d("Oncreate","permisos normales");
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, SOLICITUD_PERMISO_CAMARA);
//        }


        //ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, SOLICITUD_PERMISO_CAMARA);
    }

    public void cameraSettings(Bundle savedInstanceState) {

    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == SOLICITUD_PERMISO_CAMARA) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.e("onRequest", "permisosCondecidos");
                cameraView.setMaxFrameSize(cam_width, cam_height);
                cameraView.enableView();

            } else {
                Snackbar.make(cameraView, "Sin acceso a la cámara, no funciona la aplicación", Snackbar.LENGTH_INDEFINITE).show();
                Log.e("onRequestPermission", "no tengo");
                 // finish();
                // Toast.makeText(this, "sin cacceso a la cámara",Toast.LENGTH_SHORT).show();

            }
        } else if (requestCode == SOLICITUD_PERMISO_ALMACENAMIENTO) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permisos Concedidos", Toast.LENGTH_SHORT).show();
                Log.e("onRequest", "permisosCondecidos");
                takePhoto(input, output);

            } else {
                Log.e("onRequestPermission", "no tengo");
                Snackbar.make(cameraView, "Sin acceso al almacenamiento, no se puede guardar imágenes", Snackbar.LENGTH_INDEFINITE).show();
                finish();
            }

        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (cameraView != null)
            cameraView.disableView();
    }


    @Override
    protected void onResume() {
        super.onResume();
        Log.e("onResume", "vamos a cargar OpenCV");
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, this);
        Log.e("onResume", "cargamos librería OpenCV");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraView != null) {
            cameraView.disableView();
        }
    }

    public void onSavedInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putInt(STATE_CAMERA_INDEX, cameraIndex);
        super.onSaveInstanceState(savedInstanceState);
    }


    @Override
    public void onCameraViewStarted(int width, int height) {
        cam_height = height;
        cam_width = width;
        processor = new Processor();

    }

    @Override
    public void onCameraViewStopped() {

    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        if (inputType == 0) {
            input = inputFrame.rgba();
        } else {
            if (reloadResource == true) {
                imageResource_ = new Mat();
                int RECURSOS_FICHEROS[] = {0, R.raw.img1, R.raw.img2};
                Bitmap bitmap = BitmapFactory.decodeResource(getResources(),
                        RECURSOS_FICHEROS[inputType]);
                //Convert the resource to an OpenCV Mat
                Utils.bitmapToMat(bitmap, imageResource_);
                Imgproc.resize(imageResource_, imageResource_, new Size(cam_width, cam_height));
                reloadResource = false;
            }
            input = imageResource_;
        }
        output = processor.process(input);
        if (saveNextImage) {//Para foto salida debe ser rgba
            takePhoto(input, output);
            saveNextImage = false;
        }
        if (inputType > 0) {
            //Es necesario que el tamaño de la salida coincida con el real de captura
            Imgproc.resize(input, output, new Size(cam_width, cam_height));
        }
        if(output.channels() == 1)
            Imgproc.cvtColor(output, output, Imgproc.COLOR_GRAY2RGBA);
        return output;
    }


    @Override
    public void onManagerConnected(int status) {
        switch (status) {
            case LoaderCallbackInterface.SUCCESS:
                Log.d(TAG, "OpenCV se cargó correctamente");
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    Log.d("TAG", "tengo permisos");
                    cameraView.setMaxFrameSize(cam_width, cam_height);
                    cameraView.enableView();
                } else {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                        Snackbar.make(view, "Se necesita permiso para usar la cámara.", Snackbar.LENGTH_INDEFINITE).setAction("OK", new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                Log.d("onCreate", "Pedimos permisos rationale");
                                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, SOLICITUD_PERMISO_CAMARA);
                            }
                        }).show();
                    } else {
                        Log.d("Oncreate", "Pedimos permisos normales");
                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, SOLICITUD_PERMISO_CAMARA);
                    }
                }
                break;
            default:
                Log.d(TAG, "OpenCv no se cargó");
                Toast.makeText(this, "OpenCV no se cargó", Toast.LENGTH_SHORT).show();
                finish();
                break;
        }

    }

    @Override
    public void onPackageInstall(int operation, InstallCallbackInterface callback) {

    }


    public boolean onTouchEvent(MotionEvent event) {
        openOptionsMenu();
        return true;
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.changeCamera:
                if (cameraIndex == CameraBridgeViewBase.CAMERA_ID_BACK) {
                    cameraIndex = CameraBridgeViewBase.CAMERA_ID_FRONT;
                } else
                    cameraIndex = CameraBridgeViewBase.CAMERA_ID_BACK;
                restartResolution();
                break;
            case R.id.resolution_800x600:
                cam_width = 800;
                cam_height = 600;
                restartResolution();
                break;
            case R.id.resolution_640x480:
                cam_width = 640;
                cam_height = 480;
                restartResolution();
                break;
            case R.id.resolution_320x240:
                cam_width = 320;
                cam_height = 240;
                restartResolution();
                break;
            case R.id.camera_Input:
                inputType = 0;
                break;
            case R.id.file1_Input:
                inputType = 1;
                reloadResource = true;
                break;
            case R.id.file2_Input:
                inputType = 2;
                reloadResource = true;
                break;
            case R.id.save_images:
                saveNextImage = true;
                break;
        }
        String msg = "W=" + Integer.toString(cam_width) + " H= " +
                Integer.toString(cam_height) + " Cam= " +
                Integer.toBinaryString(cameraIndex);
        Toast.makeText(MainActivity.this, msg,
                Toast.LENGTH_LONG).show();
        return true;
    }

    public void restartResolution() {
        cameraView.disableView();
        cameraView.setMaxFrameSize(cam_width, cam_height);
        cameraView.enableView();
    }

    private void takePhoto(final Mat input, final Mat output) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            // Determina la ruta para crear los archivos
            final long currentTimeMillis = System.currentTimeMillis();
            final String appName = getString(R.string.app_name);
            final String galleryPath = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES).toString();
            final String albumPath = galleryPath + "/" + appName;
            final String photoPathIn = albumPath + "/In_" + currentTimeMillis
                    + ".png";
            final String photoPathOut = albumPath + "/Out_" + currentTimeMillis
                    + ".png";
            // Asegurarse que el directorio existe
            File album = new File(albumPath);
            if (!album.isDirectory() && !album.mkdirs()) {
                Log.d(TAG, "Error al crear el directorio " + albumPath);
                return;
            }
            // Intenta crear los archivos
            Mat mBgr = new Mat();
            if (output.channels() == 1)
                Imgproc.cvtColor(output, mBgr, Imgproc.COLOR_GRAY2BGR, 3);
            else
                Imgproc.cvtColor(output, mBgr, Imgproc.COLOR_RGBA2BGR, 3);
            if (!Imgcodecs.imwrite(photoPathOut, mBgr)) {
                Log.d(TAG, "Fallo al guardar " + photoPathOut);
            }
            if (input.channels() == 1)
                Imgproc.cvtColor(input, mBgr, Imgproc.COLOR_GRAY2BGR, 3);
            else
                Imgproc.cvtColor(input, mBgr, Imgproc.COLOR_RGBA2BGR, 3);
            if (!Imgcodecs.imwrite(photoPathIn, mBgr))
                Log.d(TAG, "Fallo al guardar " + photoPathIn);
            mBgr.release();
            return;
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Snackbar.make(cameraView, "Se necesita permiso para usar la cámara.", Snackbar.LENGTH_INDEFINITE).setAction("OK", new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Log.d("onCreate", "permisos rationale");
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, SOLICITUD_PERMISO_ALMACENAMIENTO);
                }
            }).show();
        } else {
            Log.d("Oncreate", "permisos normales");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, SOLICITUD_PERMISO_ALMACENAMIENTO);
        }

    }
}



