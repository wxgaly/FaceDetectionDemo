package wxgaly.android.facedetectiondemo;

import android.app.Application;

/**
 * wxgaly.android.facedetectiondemo.
 *
 * @author Created by WXG on 2018/8/22 022 10:53.
 * @version V1.0
 */
public class FaceApplication extends Application {

    public FaceApplication() {
        CrashHandler crashHandler = CrashHandler.getInstance();
//        crashHandler.init(getApplicationContext());
    }
}
