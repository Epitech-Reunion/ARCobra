package com.epitech.cobra.arcore_coding_club.augmentedfaces;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedFace;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraConfig;
import com.google.ar.core.CameraConfigFilter;
import com.google.ar.core.Config;
import com.google.ar.core.Config.AugmentedFaceMode;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.epitech.cobra.arcore_coding_club.common.helpers.CameraPermissionHelper;
import com.epitech.cobra.arcore_coding_club.common.helpers.DisplayRotationHelper;
import com.epitech.cobra.arcore_coding_club.common.helpers.FullScreenHelper;
import com.epitech.cobra.arcore_coding_club.common.helpers.SnackbarHelper;
import com.epitech.cobra.arcore_coding_club.common.helpers.TrackingStateHelper;
import com.epitech.cobra.arcore_coding_club.common.rendering.BackgroundRenderer;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class AugmentedFacesActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
    private static final String TAG = AugmentedFacesActivity.class.getSimpleName();
    private GLSurfaceView surfaceView;
    private boolean installRequested;
    private Session session;
    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private DisplayRotationHelper displayRotationHelper;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
    private final AugmentedFaceRenderer augmentedFaceRenderer = new AugmentedFaceRenderer();
    private static final float[] DEFAULT_COLOR = new float[] {0f, 0f, 0f, 0f};

    /**
     *   STEP 1 : Créer un endroit où stocker les objets en réalité augmentée
     */

    private final AugmentedObjects augmentedObjects = new AugmentedObjects();

    /**
     *
     */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        surfaceView = findViewById(R.id.surfaceview);
        displayRotationHelper = new DisplayRotationHelper(this);
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        surfaceView.setRenderer(this);
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        surfaceView.setWillNotDraw(false);
        installRequested = false;
    }

    @Override
    protected void onDestroy() {
        if (session != null) {
            session.close();
            session = null;
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (session == null) {
            Exception exception = null;
            String message = null;
            try {
                switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                    return;
                }
                session = new Session(this, EnumSet.noneOf(Session.Feature.class));
                CameraConfigFilter cameraConfigFilter = new CameraConfigFilter(session);
                cameraConfigFilter.setFacingDirection(CameraConfig.FacingDirection.FRONT);
                List<CameraConfig> cameraConfigs = session.getSupportedCameraConfigs(cameraConfigFilter);
                if (!cameraConfigs.isEmpty()) {
                    session.setCameraConfig(cameraConfigs.get(0));
                } else {
                    message = "This device does not have a front-facing (selfie) camera";
                    exception = new UnavailableDeviceNotCompatibleException(message);
                }
                configureSession();

            } catch (UnavailableArcoreNotInstalledException
                    | UnavailableUserDeclinedInstallationException e) {
                message = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update this app";
                exception = e;
            } catch (UnavailableDeviceNotCompatibleException e) {
                message = "This device does not support AR";
                exception = e;
            } catch (Exception e) {
                message = "Failed to create AR session";
                exception = e;
            }
            if (message != null) {
                messageSnackbarHelper.showError(this, message);
                Log.e(TAG, "Exception creating session", exception);
                return;
            }
        }
        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.");
            session = null;
            return;
        }
        surfaceView.onResume();
        displayRotationHelper.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        try {
            backgroundRenderer.createOnGlThread(this);
            augmentedFaceRenderer.createOnGlThread(this, "models/meshes/freckles.png");
            augmentedFaceRenderer.setMaterialProperties(0.0f, 1.0f, 0.1f, 6.0f);

            /**
             *   STEP 2 : Créer les objets en réalité augmenté en les ajoutant à l'espace de stockage créé à l'étape 1
             */

            augmentedObjects.addObject(
                    this,
                    "models/objects/forehead_left.obj",
                    "models/materials/ear_fur.png",
                    AugmentedObjects.FACE_AREA.FOREHEAD_LEFT
            );
            augmentedObjects.addObject(
                    this,
                    "models/objects/forehead_right.obj",
                    "models/materials/ear_fur.png",
                    AugmentedObjects.FACE_AREA.FOREHEAD_RIGHT
            );
            augmentedObjects.addObject(
                    this,
                    "models/objects/nose.obj",
                    "models/materials/nose_fur.png",
                    AugmentedObjects.FACE_AREA.CENTER
            );

            /**
             *
             */


        } catch (IOException e) {
            Log.e(TAG, "Failed to read an asset file", e);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        if (session == null)
            return;
        displayRotationHelper.updateSessionIfNeeded(session);
        try {
            session.setCameraTextureName(backgroundRenderer.getTextureId());
            Frame frame = session.update();
            Camera camera = frame.getCamera();
            float[] projectionMatrix = new float[16];
            camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);
            float[] viewMatrix = new float[16];
            camera.getViewMatrix(viewMatrix, 0);
            final float[] colorCorrectionRgba = new float[4];
            frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);
            backgroundRenderer.draw(frame);
            trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());
            Collection<AugmentedFace> faces = session.getAllTrackables(AugmentedFace.class);
            for (AugmentedFace face : faces) {
                if (face.getTrackingState() != TrackingState.TRACKING) {
                    break;
                }
                float scaleFactor = 1.0f;
                GLES20.glDepthMask(false);
                float[] modelMatrix = new float[16];
                face.getCenterPose().toMatrix(modelMatrix, 0);
                augmentedFaceRenderer.draw(projectionMatrix, viewMatrix, modelMatrix, colorCorrectionRgba, face);
                augmentedObjects.updateObjectsMatrix(face);

                /**
                 *   STEP 3 : Mettre à jour les objets à chaque instant pour qu'ils puissent bouger en fonction du visage détecté
                 */

                augmentedObjects.updateObject(
                        "models/objects/forehead_left.obj",
                        /* scale */ new float[]{1.0f, 1.0f, 1.0f},
                        /* rotate */ new float[]{/* angle= */ 0.0f, 1.0f, 0.0f, 0.0f},
                        /* translate */ new float[]{0.0f, 0.0f, 0.0f}
                );
                augmentedObjects.updateObject(
                        "models/objects/forehead_right.obj",
                        null,
                        null,
                        null
                );
                augmentedObjects.updateObject(
                        "models/objects/nose.obj",
                        null,
                        null,
                        null
                );

                /**
                 *
                 */

                augmentedObjects.drawObjects(viewMatrix, projectionMatrix, colorCorrectionRgba, DEFAULT_COLOR);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Exception on the OpenGL thread", t);
        } finally {
            GLES20.glDepthMask(true);
        }
    }

    private void configureSession() {
        Config config = new Config(session);
        config.setAugmentedFaceMode(AugmentedFaceMode.MESH3D);
        session.configure(config);
    }
}
