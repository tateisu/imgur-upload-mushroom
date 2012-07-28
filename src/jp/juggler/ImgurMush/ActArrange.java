package jp.juggler.ImgurMush;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.regex.Pattern;

import jp.juggler.ImgurMush.data.ResizePreset;
import jp.juggler.ImgurMush.helper.BaseActivity;
import jp.juggler.ImgurMush.helper.ImageTempDir;
import jp.juggler.ImgurMush.helper.PreviewLoader;

import jp.juggler.util.LogCategory;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

public class ActArrange extends BaseActivity{
	static final LogCategory log = new LogCategory("ActArrange");
	static final boolean debug = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		initUI();
		init_page();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		init_page();
	}

	@Override
	protected void onStart() {
		super.onStart();
		avPreview.setShowing(true);

	}

	@Override
	protected void onStop() {
		super.onStop();
		avPreview.setShowing(false);
	}

	@Override
	protected void onPause() {
		super.onPause();
		// 現在の画面の状態をintentに保存する
		save_status();
		// リサイズ指定を保存する
		SharedPreferences.Editor e = pref().edit();
		if( resize_preset == null ){
			e.putInt(PrefKey.KEY_LAST_RESIZE_MODE,-1);
		}else{
			e.putInt(PrefKey.KEY_LAST_RESIZE_MODE,resize_preset.mode);
			e.putInt(PrefKey.KEY_LAST_RESIZE_VALUE,resize_preset.value);
		}
		e.commit();
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// リサイズ指定が返されたらそれをUIに反映させる
		if( requestCode == REQUEST_RESIZE_PRESET && resultCode == RESULT_OK && data != null){
			int mode = data.getIntExtra(PrefKey.EXTRA_RESIZE_PRESET_MODE,-1);
			int value = data.getIntExtra(PrefKey.EXTRA_RESIZE_PRESET_VALUE,-1);
			set_resize(mode,value);
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	////////////////////////////////////////////////////////

	static final int REQUEST_RESIZE_PRESET = 1;
	static final int SEEKBAR_MAX = 10000;
	static final int SEEKBAR_KEY_INCREMENT = 100;
	final ActArrange act = this;

	jp.juggler.ImgurMush.ArrangeView avPreview;
	View btnRotateLeft;
	View btnRotateRight;
	View btnSave;
	Button btnResize;
	SeekBar sbCropLeft;
	SeekBar sbCropRight;
	SeekBar sbCropTop;
	SeekBar sbCropBottom;
	TextView tvCropLeft;
	TextView tvCropTop;
	TextView tvCropRight;
	TextView tvCropBottom;
	String src_path;


	void save_status(){
		log.d("save_status");
		Intent intent = getIntent();
		intent.putExtra( PrefKey.EXTRA_IS_STATUS_SAVE , true );
		intent.putExtra( PrefKey.EXTRA_EDIT_ROTATE, avPreview.getRotate() );
		intent.putExtra( PrefKey.EXTRA_CROP_L, sbCropLeft.getProgress() );
		intent.putExtra( PrefKey.EXTRA_CROP_R, sbCropRight.getProgress() );
		intent.putExtra( PrefKey.EXTRA_CROP_T, sbCropTop.getProgress() );
		intent.putExtra( PrefKey.EXTRA_CROP_B, sbCropBottom.getProgress() );
		setIntent(intent);
	}

	void initUI(){
		setContentView(R.layout.act_arrange);

		avPreview = (jp.juggler.ImgurMush.ArrangeView)findViewById(R.id.preview);
		btnRotateLeft = findViewById(R.id.btnRotateLeft);
		btnRotateRight = findViewById(R.id.btnRotateRight);
		btnSave = findViewById(R.id.btnSave);
		btnResize = (Button)findViewById(R.id.btnResize);
		sbCropLeft = (SeekBar)findViewById(R.id.sbCropLeft);
		sbCropRight = (SeekBar)findViewById(R.id.sbCropRight);
		sbCropTop = (SeekBar)findViewById(R.id.sbCropTop);
		sbCropBottom = (SeekBar)findViewById(R.id.sbCropBottom);
		tvCropLeft = (TextView)findViewById(R.id.tvCropLeft);
		tvCropTop = (TextView)findViewById(R.id.tvCropTop);
		tvCropRight = (TextView)findViewById(R.id.tvCropRight);
		tvCropBottom = (TextView)findViewById(R.id.tvCropBottom);

		seekbar_busy = true;
		sbCropLeft.setOnSeekBarChangeListener(seekbar_listener);
		sbCropRight.setOnSeekBarChangeListener(seekbar_listener);
		sbCropTop.setOnSeekBarChangeListener(seekbar_listener);
		sbCropBottom.setOnSeekBarChangeListener(seekbar_listener);
		sbCropLeft.setMax(SEEKBAR_MAX);
		sbCropRight.setMax(SEEKBAR_MAX);
		sbCropTop.setMax(SEEKBAR_MAX);
		sbCropBottom.setMax(SEEKBAR_MAX);
		sbCropLeft.setKeyProgressIncrement(SEEKBAR_KEY_INCREMENT);
		sbCropRight.setKeyProgressIncrement(SEEKBAR_KEY_INCREMENT);
		sbCropTop.setKeyProgressIncrement(SEEKBAR_KEY_INCREMENT);
		sbCropBottom.setKeyProgressIncrement(SEEKBAR_KEY_INCREMENT);
		seekbar_busy = false;

		findViewById(R.id.btnCancel).setOnClickListener(new View.OnClickListener(){
			@Override public void onClick(View v) {
				finish();
			}
		});

		btnRotateLeft.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View v) {
				avPreview.setRotate(avPreview.getRotate()-1);
				seekbar_busy = true;
				int l = sbCropLeft.getProgress();
				int r = sbCropRight.getProgress();
				int t = sbCropTop.getProgress();
				int b = sbCropBottom.getProgress();
				sbCropTop.setProgress(r);
				sbCropRight.setProgress(b);
				sbCropBottom.setProgress(l);
				sbCropLeft.setProgress(t);
				seekbar_busy = false;
				update_crop_preview();
			}
		});
		btnRotateRight.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View v) {
				avPreview.setRotate(avPreview.getRotate()+1);
				seekbar_busy = true;
				int l = sbCropLeft.getProgress();
				int r = sbCropRight.getProgress();
				int t = sbCropTop.getProgress();
				int b = sbCropBottom.getProgress();
				sbCropTop.setProgress(l);
				sbCropRight.setProgress(t);
				sbCropBottom.setProgress(r);
				sbCropLeft.setProgress(b);
				seekbar_busy = false;
				update_crop_preview();
			}
		});

		btnResize.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View v) {
				startActivityForResult(new Intent(act,ActResizePreset.class),REQUEST_RESIZE_PRESET);
			}
		});

		btnSave.setOnClickListener(new OnClickListener() {
			@Override public void onClick(View v) {
				save();
			}
		});

	}

	void init_page(){
		setResult(RESULT_CANCELED);

		// プリセット指定をロードする
		SharedPreferences pref = pref();
		int mode = pref.getInt(PrefKey.KEY_LAST_RESIZE_MODE,-1);
		int value = pref.getInt(PrefKey.KEY_LAST_RESIZE_VALUE,-1);
		set_resize(mode,value);

		// その他の初期化はレイアウトが終わってから
		delay_init.run();
	}


	Runnable delay_init = new Runnable() {
		@Override
		public void run() {
			avPreview.setImageBitmap(null);
			avPreview.setRotate(0);
			// 切り抜きシークバーをリセット
			seekbar_busy = true;
			sbCropLeft.setProgress(0);
			sbCropRight.setProgress(0);
			sbCropTop.setProgress(0);
			sbCropBottom.setProgress(0);
			seekbar_busy = false;

			// 各種ボタンを無効化
			btnSave.setEnabled(false);
			btnRotateLeft.setEnabled(false);
			btnRotateRight.setEnabled(false);
			btnResize.setEnabled(false);
			sbCropLeft.setEnabled(false);
			sbCropRight.setEnabled(false);
			sbCropTop.setEnabled(false);
			sbCropBottom.setEnabled(false);

			// レイアウトが終わるまで待つ
			act.ui_handler.removeCallbacks(delay_init);
			int w = avPreview.getWidth();
			int h = avPreview.getHeight();
			if( w <1 || h<1 ){
				act.ui_handler.postDelayed(delay_init,66);
				return;
			}

			Intent intent = getIntent();
			src_path = intent.getStringExtra(PrefKey.EXTRA_SRC_PATH);
			if( intent.getBooleanExtra(PrefKey.EXTRA_IS_STATUS_SAVE,false) ){
				avPreview.setRotate( intent.getIntExtra(PrefKey.EXTRA_EDIT_ROTATE,0));
				seekbar_busy = true;
				sbCropLeft.setProgress(intent.getIntExtra(PrefKey.EXTRA_CROP_L,0));
				sbCropRight.setProgress(intent.getIntExtra(PrefKey.EXTRA_CROP_R,0));
				sbCropTop.setProgress(intent.getIntExtra(PrefKey.EXTRA_CROP_T,0));
				sbCropBottom.setProgress(intent.getIntExtra(PrefKey.EXTRA_CROP_B,0));
				seekbar_busy = false;
			}


			int preview_image_max_wh =(w>h?w:h);
			PreviewLoader.load(act,src_path,false,preview_image_max_wh,preview_image_max_wh,new PreviewLoader.Callback() {
				@Override
				public void onMeasure(int w, int h) {
				}

				@Override
				public void onLoad(Bitmap bitmap) {
					btnSave.setEnabled(true);
					btnRotateLeft.setEnabled(true);
					btnRotateRight.setEnabled(true);
					sbCropLeft.setEnabled(true);
					sbCropRight.setEnabled(true);
					sbCropTop.setEnabled(true);
					sbCropBottom.setEnabled(true);
					btnResize.setEnabled(true);

					//
					avPreview.setImageBitmap(bitmap);
					update_crop_preview();
				}
			});
		}
	};

	static final float getCropFromSeekBar(SeekBar sb){
		return sb.getProgress() /(float)SEEKBAR_MAX;
	}

	boolean seekbar_busy = false;
	void update_crop_preview(){
		if(!seekbar_busy){
			float l = getCropFromSeekBar(sbCropLeft);
			float r = getCropFromSeekBar(sbCropRight);
			float t = getCropFromSeekBar(sbCropTop);
			float b = getCropFromSeekBar(sbCropBottom);
			avPreview.setCrop(l,r,t,b);
			if( l+r >= 1.0f || t+b >= 1.0f ){
				btnSave.setEnabled(false);
			}else{
				btnSave.setEnabled(true);
			}
			tvCropLeft.setText(format_crop_ratio(l));
			tvCropTop.setText(format_crop_ratio(t));
			tvCropRight.setText(format_crop_ratio(r));
			tvCropBottom.setText(format_crop_ratio(b));
		}
	}
	static final String format_crop_ratio(float v){
		if( v > 0.99999f ) return "99.99%";
		return String.format("%1$.2f%%",v*100);
	}

	SeekBar.OnSeekBarChangeListener seekbar_listener = new SeekBar.OnSeekBarChangeListener() {
		@Override public void onStopTrackingTouch(SeekBar seekBar) {
			update_crop_preview();
		}
		@Override public void onStartTrackingTouch(SeekBar seekBar) {
			update_crop_preview();
		}
		@Override public void onProgressChanged(SeekBar seekBar, int progress,boolean fromUser) {
			update_crop_preview();
		}
	};

	ResizePreset resize_preset;

	void set_resize(int mode,int value){
		if( mode < 0 ){
			resize_preset = null;
			btnResize.setText(getString(R.string.resize_disabled));
		}else{
			resize_preset = new ResizePreset();
			resize_preset.mode = mode;
			resize_preset.value = value;
			btnResize.setText(resize_preset.makeTitle(act));
		}
	}

	////////////////////////////////////////////////////////////////////

	int getJPEGQuality(){
		int v = 85;
		try{
			v = Integer.parseInt( act.pref().getString(PrefKey.KEY_JPEG_QUALITY,null) ,10 );
		}catch(Throwable ex){
		}
		return v<10?10:v>100?100:v;
	}
	
	void save(){
		final int rot_mode = avPreview.getRotate();
		final int quality = getJPEGQuality();
		final float crop_l = getCropFromSeekBar(sbCropLeft);
		final float crop_r = getCropFromSeekBar(sbCropRight);
		final float crop_t = getCropFromSeekBar(sbCropTop);
		final float crop_b = getCropFromSeekBar(sbCropBottom);

		// 進捗表示
		final ProgressDialog progress_dialog = new ProgressDialog(act);
		progress_dialog.setIndeterminate(true);
		progress_dialog.setTitle(R.string.edit_progress_title);
		progress_dialog.setCancelable(true);
		act.dialog_manager.show_dialog(progress_dialog);

		new Thread(){
			// 進捗表示がキャンセルされたなら真
			boolean isCancelled(){
				if(! progress_dialog.isShowing() ) return true;
				return false;
			}

			@Override
			public void run() {
				try{
					BitmapFactory.Options options = new BitmapFactory.Options();
					options.inJustDecodeBounds = false;

					options.inPurgeable = true;
					options.inTargetDensity = 0;
					options.inDensity = 0;
					options.inDither = true;
					options.inScaled = false;
					options.inPreferredConfig = Bitmap.Config.ARGB_8888;

					// 大きすぎる画像を処理できない可能性があるため、sample size を変えながら何度か試す
					for(int sample_size=1;;sample_size<<=1){
						if( isCancelled() ) break;
						Bitmap bitmap_src = null;
						Bitmap bitmap_dst = null;
						try{
							// 入力ビットマップをロードする
							options.inSampleSize = sample_size;
							bitmap_src = BitmapFactory.decodeFile(src_path, options);
							if(bitmap_src == null ) throw new RuntimeException("bitmap decode failed.");
							// ロードできたならサイズが分かるはず
							int source_w = bitmap_src.getWidth();
							int source_h = bitmap_src.getHeight();
							if(source_w <1 || source_h<1) throw new RuntimeException("bitmap decode failed.");

							if( isCancelled() ) break;

							// 切り抜き量 ピクセル単位
							int source_crop_l;
							int source_crop_r;
							int source_crop_t;
							int source_crop_b;
							switch(rot_mode){
							case 0: default:
								source_crop_t = (int)(0.5f + source_h * crop_t);
								source_crop_r = (int)(0.5f + source_w * crop_r);
								source_crop_b = (int)(0.5f + source_h * crop_b);
								source_crop_l = (int)(0.5f + source_w * crop_l);
								break;
							case 1:
								source_crop_t = (int)(0.5f + source_h * crop_r);
								source_crop_r = (int)(0.5f + source_w * crop_b);
								source_crop_b = (int)(0.5f + source_h * crop_l);
								source_crop_l = (int)(0.5f + source_w * crop_t);
								break;
							case 2:
								source_crop_t = (int)(0.5f + source_h * crop_b);
								source_crop_r = (int)(0.5f + source_w * crop_l);
								source_crop_b = (int)(0.5f + source_h * crop_t);
								source_crop_l = (int)(0.5f + source_w * crop_r);
								break;
							case 3:
								source_crop_t = (int)(0.5f + source_h * crop_l);
								source_crop_r = (int)(0.5f + source_w * crop_t);
								source_crop_b = (int)(0.5f + source_h * crop_r);
								source_crop_l = (int)(0.5f + source_w * crop_b);
								break;
							}
							// 切り抜き量の正規化
							if( source_crop_l >= source_w ) source_crop_l = source_w -1;
							if( source_crop_l < 0 ) source_crop_l = 0;
							if( source_crop_r >= source_w - source_crop_l) source_crop_r = source_w - source_crop_l - 1;
							//
							if( source_crop_t >= source_h ) source_crop_t = source_h -1;
							if( source_crop_t < 0 ) source_crop_t = 0;
							if( source_crop_b >= source_h - source_crop_t) source_crop_b = source_h - source_crop_t - 1;
							// 切り抜き後のサイズ
							int cropped_w = source_w - source_crop_l - source_crop_r;
							int cropped_h = source_h - source_crop_t - source_crop_b;
							if(cropped_w < 1) cropped_w =1;
							if(cropped_h < 1) cropped_h =1;
							float cropped_aspect = cropped_w/(float)cropped_h;

							log.d("crop left=%s,right=%s,width=%s,src=%s",source_crop_l,source_crop_r,cropped_w,source_w);
							log.d("crop top=%s,bottom=%s,height=%s,src=%s",source_crop_t,source_crop_b,cropped_h,source_h);

							// リサイズの倍率とピクセル幅を計算する
							int resized_w = cropped_w;
							int resized_h = cropped_h;
							float scale = 1.0f;
							if(resize_preset != null ){
								final int limit = resize_preset.value;
								switch(resize_preset.mode){
								case 0: default:
									scale = resize_preset.value/(float)100;
									resized_w = (int)(0.5f + resized_w * scale);
									resized_h = (int)(0.5f + resized_h * scale);
									break;
								case 1:
									if( cropped_w >= cropped_h ){
										if( cropped_w > limit ){
											scale = limit / (float)cropped_w;
											resized_w = limit;
											resized_h = (int)(0.5f + limit / cropped_aspect );
										}
									}else{
										if( cropped_h > limit ){
											scale = limit / (float)cropped_h;
											resized_w = (int)(0.5f + limit * cropped_aspect );
											resized_h = limit;
										}
									}
									break;
								case 2:
									if( cropped_w <= cropped_h ){
										if( cropped_w > limit ){
											scale = limit / (float)cropped_w;
											resized_w = limit;
											resized_h = (int)(0.5f + limit / cropped_aspect );
										}
									}else{
										if( cropped_h > limit ){
											scale = limit / (float)cropped_h;
											resized_w = (int)(0.5f + limit * cropped_aspect );
											resized_h = limit;
										}
									}
									break;
								}
								// 1px未満にならないようにクリップ
								if( resized_w < 1 ) resized_w = 1;
								if( resized_h < 1 ) resized_h = 1;
							}

							boolean is_jpeg = check_jpeg(src_path);
							
							// 画像がもともとJPEGで、回転なし、切り抜きもリサイズもなし…ってことは加工が必要ない
							if( is_jpeg
							&&  rot_mode == 0
							&&  resized_w >= source_w
							&&  resized_h >= source_h
							){
								act.ui_handler.post(new Runnable() {
									@Override
									public void run() {
										if(isFinishing()) return;
										progress_dialog.dismiss();
										Intent intent = new Intent();
										intent.putExtra(PrefKey.EXTRA_DST_PATH,src_path);
										setResult(RESULT_OK,intent);
										finish();
									}
								});
								return;
							}

							// 90/270度回転の場合、出力サイズは縦横が入れ替わる
							if( (rot_mode &1) != 0 ){
								int tmp = resized_w; resized_w = resized_h;resized_h = tmp;
							}

							// 入力画像を出力画像にdrawする際の回転/平行移動/スケールをマトリクスにまとめる
							Matrix m = new Matrix();
							switch(rot_mode){
							case 0:
								m.postTranslate( -source_crop_l,-source_crop_t);
								break;
							case 1:
								m.postRotate(rot_mode * 90);
								m.postTranslate( source_h,0);
								m.postTranslate( -source_crop_b,-source_crop_l);
								break;
							case 2:
								m.postRotate(rot_mode * 90);
								m.postTranslate( source_w,source_h);
								m.postTranslate( -source_crop_r,-source_crop_b);
								break;
							case 3:
								m.postRotate(rot_mode * 90);
								m.postTranslate( 0,source_w);
								m.postTranslate( -source_crop_t,-source_crop_r);
								break;
							}
							m.postScale(scale,scale);

							if(debug){
								float[] values = new float[9];
								m.getValues(values);
								log.d("matrix %s,%s,%s",values[0],values[1],values[2]);
								log.d("matrix %s,%s,%s",values[3+0],values[3+1],values[3+2]);
								log.d("matrix %s,%s,%s",values[6+0],values[6+1],values[6+2]);
							}

							// 出力ビットマップを生成する
							bitmap_dst = Bitmap.createBitmap(resized_w,resized_h,Bitmap.Config.ARGB_8888);
							if( bitmap_dst == null ) throw new RuntimeException("bitmap generation failed.");
							int dst_w = bitmap_dst.getWidth();
							int dst_h = bitmap_dst.getHeight();
							if( dst_w < 1 || dst_h < 1) throw new RuntimeException("bitmap generation failed.");

							// 生成できたので画像を転送する
							Canvas canvas = new Canvas(bitmap_dst);
							Paint paint = new Paint();
							paint.setFilterBitmap(true);
							canvas.drawBitmap(bitmap_src,m,paint);

							try{
								// 出力ファイルパスの確定
								final File dst_path = ImageTempDir.makeTempFile(act);
								if(dst_path==null){
									// 出力ファイルを作成できなかった
									// エラー表示は既に行われている
									break;
								}
								// JPEGファイルを出力する
								saveJPEG(dst_path,bitmap_dst,quality);

								// 完了したので画面を閉じる
								act.ui_handler.post(new Runnable() {
									@Override
									public void run() {
										if(isFinishing()) return;
										progress_dialog.dismiss();
										Intent intent = new Intent();
										intent.putExtra(PrefKey.EXTRA_DST_PATH,dst_path.getAbsolutePath());
										setResult(RESULT_OK,intent);
										finish();
									}
								});
								return;

							}catch(IOException ex){
								// ファイル出力に関するエラーはリトライ対象にならない
								act.report_ex(ex);
								break;
							}

						}catch(Throwable ex){
							ex.printStackTrace();
							continue;
						}finally{
							// ビットマップを解放しておく
							if( bitmap_src != null ) bitmap_src.recycle();
							if( bitmap_dst != null ) bitmap_dst.recycle();
						}
					}
				}finally{
					// 進捗表示を消す
					act.ui_handler.post(new Runnable() {
						@Override
						public void run() {
							if(isFinishing()) return;
							progress_dialog.dismiss();
						}
					});
				}
			}
		}.start();
	}

	// JPEGファイルに保存する
	static void saveJPEG(File f,Bitmap b,int q) throws IOException{
		FileOutputStream fos = new FileOutputStream(f);
		try{
			b.compress(Bitmap.CompressFormat.JPEG,q,fos);
		}finally{
			fos.close();
		}
	}
	
	// ファイルがJPEGなら真を返す
	boolean check_jpeg(String path){
		try{
			FileInputStream fi = new FileInputStream(src_path);
			try{
				int c1 = fi.read();
				int c2 = fi.read();
				if( c1 == 0xff
				&&  c2 == 0xD8
				){
					return true;
				}
			}finally{
				fi.close();
			}
		}catch(Throwable ex){
			ex.printStackTrace();
		}
		return false;
	}

}
