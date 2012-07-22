package jp.juggler.ImgurMush.helper;

import jp.juggler.ImgurMush.BaseActivity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class PreviewLoader{
	
	public interface Callback{
		void onMeasure(int w,int h);
		void onLoad(Bitmap bitmap);
	}
	
	public static void load(final BaseActivity act, final String path,final boolean measure_only,final int max_w,final int max_h,final Callback callback){
		new Thread(){

			@Override
			public void run() {
		    	try{
					BitmapFactory.Options options = new BitmapFactory.Options();

					// 画像の大きさだけ取得
					options.inJustDecodeBounds = true;
					options.outWidth =0;
					options.outHeight =0;
					BitmapFactory.decodeFile(path, options);

					int w = options.outWidth;
					int h = options.outHeight;
				    if( w < 1 || h < 1 ) throw new RuntimeException("bitmap decode failed.");

				    final int orig_w = w;
				    final int orig_h = h;
				    act.ui_handler.post(new Runnable() {
						@Override
						public void run() {
							if(act.isFinishing()) return;
						    callback.onMeasure(orig_w,orig_h);
						}
					});
				    
				    if(!measure_only){
					    int shift = 1;
						while( w > max_w || h > max_h ){
							++shift;
							w /= 2;
							h /= 2;
						}
						
						// 今度は画像を読み込む
						options.inJustDecodeBounds = false;
						options.inSampleSize = shift;
						options.inPurgeable = true;
						options.inTargetDensity = 0;
						options.inDensity = 0;
						options.inDither =true;
						options.inScaled = false;
						final Bitmap image = BitmapFactory.decodeFile(path, options);
					    if(image == null ) throw new RuntimeException("bitmap decode failed.");
					    
					    act.ui_handler.post(new Runnable() {
							
							@Override
							public void run() {
								if(act.isFinishing()) return;
							    callback.onLoad(image);
							}
						});
				    }
				}catch(Throwable ex){
					act.report_ex(ex);
				}
			}
		}.start();
	}
}
