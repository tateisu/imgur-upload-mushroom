package jp.juggler.ImgurMush;

import jp.juggler.util.LogCategory;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;

public class ArrangeView extends View{

	public ArrangeView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context);
	}

	public ArrangeView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	public ArrangeView(Context context) {
		super(context);
		init(context);
	}

	void init(Context context){
		density = context.getResources().getDisplayMetrics().density;
		handler = new Handler();
		//
		paint_bitmap.setFilterBitmap(true);
		//
		paint_shadow.setAntiAlias(false);
		paint_shadow.setColor(0x80000000);

		paint_line.setAntiAlias(false);
	}

	Bitmap bitmap;
	int rot_mode =0; // 0:回転なし 1:時計回り90度 2:180度 3:反時計回り90度
	float density;
	Handler handler;


	public void setImageBitmap(Bitmap b){
		bitmap = b;
		invalidate();
	}
	public void setRotate(int r){
		rot_mode = r&3;
		invalidate();
	}
	public int getRotate(){
		return rot_mode;
	}
	float crop_l;
	float crop_r;
	float crop_t;
	float crop_b;

	public void setCrop(float left,float right,float top,float bottom){
		crop_l = left;
		crop_r = right;
		crop_t = top;
		crop_b = bottom;
		invalidate();
	}

	Rect rect = new Rect();
	Matrix matrix = new Matrix();
	Paint paint_bitmap = new Paint();
	Paint paint_shadow = new Paint();
	Paint paint_line = new Paint();
	LogCategory log = new LogCategory("view");
	@Override
	protected void onDraw(Canvas canvas) {
		// 画像のサイズ
		if( bitmap == null ) return;
		int img_w = bitmap.getWidth();
		int img_h = bitmap.getHeight();
		if( img_w <1 || img_h < 1 ) return;

		// 回転後のサイズ
		if( (rot_mode & 1) != 0 ){
			int tmp = img_w;img_w = img_h;img_h = tmp;
		}

		// canvasのサイズ
		final int canvas_w = getWidth();
		final int canvas_h = getHeight();

		// パディング
		final int preview_padding = to_int(density * 2);

		// パディングを引いたサイズ
		if( canvas_w < preview_padding*2 || canvas_h < preview_padding*2 ) return;
		int preview_max_w = (canvas_w- preview_padding*2);
		int preview_max_h = (canvas_h- preview_padding*2);

		// 縦横比
		float img_aspect = img_w/(float)img_h;
		float preview_max_aspect = preview_max_w/(float)preview_max_h;

		// 画像をcanvasに合わせてリサイズ
		int preview_w;
		int preview_h;
		float scale;
		if( img_aspect >= preview_max_aspect ){
			// 画像のほうが表示領域より横長なので、横幅基準でリサイズ
			scale = preview_max_w/(float)img_w;
			preview_w = preview_max_w;
			preview_h = to_int(preview_max_w / img_aspect);
		}else{
			// 画像のほうが表示領域より縦長なので、縦幅基準でリサイズ
			scale = preview_max_h/(float)img_h;
			preview_w = to_int(preview_max_h * img_aspect);
			preview_h = preview_max_h;
		}
		if(preview_w < 1 ) preview_w = 1;
		if(preview_h < 1 ) preview_h = 1;

		// 中央揃えを考慮した描画位置
		final int offset_x = to_int(preview_padding + (preview_max_w - preview_w)/2);
		final int offset_y = to_int(preview_padding + (preview_max_h - preview_h)/2);

		// 画像を描画するためのマトリクスを計算
		matrix.reset();
		matrix.postScale(scale,scale);
		matrix.postRotate(rot_mode * 90);
		switch(rot_mode){
		case 1:
			matrix.postTranslate( preview_w,0);
			break;
		case 2:
			matrix.postTranslate( preview_w,preview_h);
			break;
		case 3:
			matrix.postTranslate( 0,preview_h);
			break;
		}
		matrix.postTranslate( offset_x,offset_y);

		// 画像を描画
		canvas.drawBitmap(bitmap, matrix, paint_bitmap);

		// draw outside of crop
		int left  =  to_int(preview_w * crop_l);
		int right =  to_int(preview_w * crop_r);
		int top   =  to_int(preview_h * crop_t);
		int bottom = to_int(preview_h * crop_b);

		if( left + right >= preview_w || top + bottom >= preview_h ){
			rect.left  = to_int(offset_x );
			rect.right = to_int(offset_x + preview_w);
			rect.top   = to_int(offset_y );
			rect.bottom = to_int(offset_y + preview_h);
			canvas.drawRect (rect,paint_shadow);
		}else{
			// top
			rect.left  = to_int(offset_x );
			rect.right = to_int(offset_x + preview_w);
			rect.top   = to_int(offset_y );
			rect.bottom = to_int(offset_y + top);
			canvas.drawRect (rect,paint_shadow);
			// bottom
			rect.top    = to_int(offset_y + preview_h - bottom );
			rect.bottom = to_int(offset_y + preview_h);
			canvas.drawRect (rect,paint_shadow);
			// left
			rect.top    = to_int(offset_y + top );
			rect.bottom = to_int(offset_y + preview_h - bottom);
			rect.left  = to_int(offset_x );
			rect.right = to_int(offset_x + left);
			canvas.drawRect (rect,paint_shadow);
			//right
			rect.left  = to_int(offset_x + preview_w -right);
			rect.right = to_int(offset_x + preview_w);
			canvas.drawRect (rect,paint_shadow);

			// draw line

			int line_width = preview_padding;
			int line_width_inside = 0;

			// top
			rect.left  = offset_x + left;
			rect.right = offset_x + preview_w -right;
			rect.top    = offset_y + top- line_width;
			rect.bottom = offset_y + top +line_width_inside ;
			canvas.drawRect (rect,paint_line);
			// bottom
			rect.top    = offset_y + preview_h - bottom -line_width_inside;
			rect.bottom = offset_y + preview_h - bottom + line_width;
			canvas.drawRect (rect,paint_line);
			// left
			rect.top = offset_y + top;
			rect.bottom = offset_y + preview_h - bottom;
			rect.left  = offset_x + left - line_width;
			rect.right = offset_x + left +line_width_inside;
			canvas.drawRect (rect,paint_line);
			// right
			rect.left  = offset_x + preview_w -right -line_width_inside;
			rect.right = offset_x + preview_w -right+ line_width;
			canvas.drawRect (rect,paint_line);
		}
	}

	static final int to_int(float f){
		return (int)(0.5f+f);
	}


	public void setShowing(boolean bShowing) {
		if( bShowing ){
			show_count = 0;
			showing_runnable.run();
		}else{
			handler.removeCallbacks(showing_runnable);
		}

	}
	int show_count = 0;

	Runnable showing_runnable = new Runnable() {

		@Override
		public void run() {
			handler.removeCallbacks(showing_runnable);
			handler.postDelayed(showing_runnable,333);
			++show_count;

//			if( ((show_count/3)&1) == 0 ){
//				setBackgroundColor(0xff333333);
//			}else{
//				setBackgroundColor(0xff444444);
//			}

			int crop_color = 0;
			switch( show_count%6 ){
			case 0: crop_color = 0xffff8800; break;
			case 1: crop_color = 0xff88ff00; break;
			case 2: crop_color = 0xff00ff88; break;
			case 3: crop_color = 0xff0088ff; break;
			case 4: crop_color = 0xff8800ff; break;
			case 5: crop_color = 0xffff0088; break;
			}
			paint_line.setColor(crop_color);

			invalidate();
		}
	};
}
