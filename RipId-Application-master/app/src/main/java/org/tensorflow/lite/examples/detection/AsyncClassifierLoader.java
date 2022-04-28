package org.tensorflow.lite.examples.detection;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.res.AssetManager;
import android.os.AsyncTask;

import java.lang.ref.WeakReference;

import org.tensorflow.lite.examples.detection.cv.Classifier;
import org.tensorflow.lite.examples.detection.cv.TensorFlowImageClassifier;

public class AsyncClassifierLoader extends AsyncTask<Void, Void, Void> {

    public static final int INPUT_SIZE = 299;
    public static final int IMAGE_MEAN = 0;
    public static final float IMAGE_STD = 255;
    public static final String INPUT_NAME = "Placeholder";
    public static final String OUTPUT_NAME = "final_result";
    public static final String MODEL_FILE = "file:///android_asset/bananaco_graph.pb";
    public static final String LABEL_FILE = "file:///android_asset/bananaco_labels.txt";

    private WeakReference<ProgressDialog> dialog;
    private WeakReference<Context> context;
    private AssetManager assets;

    private Classifier classifier;
    private Callback callback;

    public AsyncClassifierLoader(Context context, Callback callback) {
        this.context = new WeakReference<>(context);
        this.assets = context.getAssets();
        this.callback = callback;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        Context context = this.context.get();
        if (context != null) {
            ProgressDialog dialog = new ProgressDialog(context);
            dialog.setMessage("Taking banana samples");
            dialog.show();
            this.dialog = new WeakReference<>(dialog);
        }
    }

    @Override
    protected Void doInBackground(Void... voids) {
        classifier = TensorFlowImageClassifier.create(
                assets,
                MODEL_FILE,
                LABEL_FILE,
                INPUT_SIZE,
                IMAGE_MEAN,
                IMAGE_STD,
                INPUT_NAME,
                OUTPUT_NAME
        );
        return null;
    }

    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);
        try {
            this.callback.onClassifierLoaded(classifier);
        } finally {
            ProgressDialog dialog = this.dialog.get();
            if (dialog != null) {
                dialog.hide();
                dialog.cancel();
            }

            this.callback = null;
            this.classifier = null;
            this.dialog = null;
            this.assets = null;
            this.context = null;
        }
    }

    interface Callback {
        void onClassifierLoaded(Classifier classifier);
    }
}
