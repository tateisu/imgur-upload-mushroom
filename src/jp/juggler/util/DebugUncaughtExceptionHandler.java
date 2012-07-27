package jp.juggler.util;

import java.lang.Thread.UncaughtExceptionHandler;

import android.content.Context;

//! Thread.setDefaultUncaughtExceptionHandler に、スタックトレース処理を行うハンドラを設定する
public class DebugUncaughtExceptionHandler implements UncaughtExceptionHandler {
	public static void set(Context context){
		// 多重設定の防止
		if( Thread.getDefaultUncaughtExceptionHandler() instanceof DebugUncaughtExceptionHandler ) return;
		// 設定する
		Thread.setDefaultUncaughtExceptionHandler(new DebugUncaughtExceptionHandler (context));
	}

	Context context;
	private UncaughtExceptionHandler mDefaultUEH;

	public DebugUncaughtExceptionHandler(Context context){
		this.context = context;
		mDefaultUEH = Thread.getDefaultUncaughtExceptionHandler();
	}

	@Override
	public void uncaughtException(Thread th, Throwable ex) {
		ex.printStackTrace();
		mDefaultUEH.uncaughtException(th, ex);
	}

}
