package jp.juggler.ImgurMush.helper;

import jp.juggler.util.HelperEnv;
import jp.juggler.util.LogCategory;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

public class PreviewLoader{
	static final LogCategory log = new LogCategory("PreviewLoader");
	public interface Callback{
		void onMeasure(int w,int h);
		void onLoad(Bitmap bitmap);
	}

	public static void load(final HelperEnv eh, final String path,final boolean measure_only,final int max_w,final int max_h,final Callback callback){
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
					eh.handler.post(new Runnable() {
						@Override public void run() {
							callback.onMeasure(orig_w,orig_h);
						}
					});

					if(!measure_only){
						int sample_size = 1;
						int limit_w = (int)(0.5f + max_w * 1.5f);
						int limit_h = (int)(0.5f + max_h * 1.5f);
						while( w > limit_w || h > limit_h ){
							sample_size <<= 1;
							w /= 2;
							h /= 2;
						}

						// 今度は画像を読み込む
						options.inJustDecodeBounds = false;
						options.inSampleSize = sample_size;
						options.inPurgeable = true;
						options.inTargetDensity = 0;
						options.inDensity = 0;
						options.inDither =true;
						options.inScaled = false;
						final Bitmap image = BitmapFactory.decodeFile(path, options);
						if(image == null ) throw new RuntimeException("bitmap decode failed.");

						log.d("inSampleSize=%d, scaled=%f,%f"
								,options.inSampleSize
								,image.getWidth()/(float)orig_w
								,image.getHeight()/(float)orig_h
						);

						eh.handler.post(new Runnable() {
							@Override public void run() {
								callback.onLoad(image);
							}
						});
					}
				}catch(Throwable ex){
					eh.report_ex(ex);
				}
			}
		}.start();
	}
}
