package jp.juggler.ImgurMush;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
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
	
	@Override
    protected void onDraw(Canvas canvas) {
		float canvas_w = getWidth();
		float canvas_h = getHeight();
        float preview_padding = density * 3;
        if( canvas_w < preview_padding*2 || canvas_h < preview_padding*2 ) return;

        float preview_max_w = (canvas_w- preview_padding*2);
        float preview_max_h = (canvas_h- preview_padding*2);
        float preview_max_aspect = preview_max_w/preview_max_h;
        
        if( bitmap != null ){
        	int img_w = bitmap.getWidth();
        	int img_h = bitmap.getHeight();
        	if( img_w >=1 && img_h >= 1 ){
	        	if( (rot_mode & 1) != 0 ){
	        		int tmp = img_w;img_w = img_h;img_h = tmp; 
	        	}
	        	float img_aspect = img_w/(float)img_h;
	        	float preview_w;
	        	float preview_h;
	        	float scale;
	        	if( img_aspect >= preview_max_aspect ){
	        		// 画像のほうが表示領域より横長なので、横幅基準でリサイズ
	        		scale = preview_max_w/img_w;
	        		preview_w = preview_max_w;
	        		preview_h = preview_max_w / img_aspect;
	        	}else{
	        		// 画像のほうが表示領域より縦長なので、縦幅基準でリサイズ
	        		scale = preview_max_h/img_h;
	        		preview_w = preview_max_h * img_aspect;
	        		preview_h = preview_max_h;
	        	}
	        	float offset_x = preview_padding + (preview_max_w - preview_w)/2;
	        	float offset_y = preview_padding + (preview_max_h - preview_h)/2;
	        	Matrix m = new Matrix();
	        	m.postScale(scale,scale);
	        	m.postRotate(rot_mode * 90);
	        	switch(rot_mode){
	        	case 1:
	        		m.postTranslate( preview_w,0);
	        		break;
	        	case 2:
	        		m.postTranslate( preview_w,preview_h);
	        		break;
	        	case 3:
	        		m.postTranslate( 0,preview_h);
	        		break;
	        	}
	        	m.postTranslate( offset_x,offset_y);
	        	Paint paint = new Paint();
	        	paint.setFilterBitmap(true);
	        	canvas.drawBitmap(bitmap, m, paint);
	        	
	        	// draw outside of crop
	        	float left  =  preview_w * crop_l;
	        	float right =  preview_w * crop_r;
	        	float top   =  preview_h * crop_t;
	        	float bottom = preview_h * crop_b;
	        	paint.setAntiAlias(true);
	        	paint.setColor(0x80000000);
	        	Rect rect = new Rect();
	        	RectF rectf = new RectF();
	        	if( left + right >= preview_w || top + bottom >= preview_h ){
	        		rect.left  = to_int(offset_x );
	        		rect.right = to_int(offset_x + preview_w); 
	        		rect.top   = to_int(offset_y );
	        		rect.bottom = to_int(offset_y + preview_h);
	        		canvas.drawRect (rect,paint);
	        	}else{
	        		// top
	        		rect.left  = to_int(offset_x );
	        		rect.right = to_int(offset_x + preview_w); 
	        		rect.top   = to_int(offset_y );
	        		rect.bottom = to_int(offset_y + top);
	        		canvas.drawRect (rect,paint);
	        		// bottom
	        		rect.top    = to_int(offset_y + preview_h - bottom );
	        		rect.bottom = to_int(offset_y + preview_h);
	        		canvas.drawRect (rect,paint);
	        		// left
	        		rect.top    = to_int(offset_y + top );
	        		rect.bottom = to_int(offset_y + preview_h - bottom);
	        		rect.left  = to_int(offset_x );
	        		rect.right = to_int(offset_x + left); 
	        		canvas.drawRect (rect,paint);
	        		//right
	        		rect.left  = to_int(offset_x + preview_w -right);
	        		rect.right = to_int(offset_x + preview_w); 
	        		canvas.drawRect (rect,paint);
	        		
	        		// draw line
	        		paint.setColor(crop_color);
	        		float line_width = preview_padding -1;
	        		

	        		// top
	        		rectf.left  = offset_x + left;
	        		rectf.right = offset_x + preview_w -right;
	        		rectf.top    = offset_y + top- line_width;
	        		rectf.bottom = offset_y + top +1 ;
	        		canvas.drawRect (rectf,paint);
	        		// bottom
	        		rectf.top    = offset_y + preview_h - bottom -1;
	        		rectf.bottom = offset_y + preview_h - bottom + line_width;
	        		canvas.drawRect (rectf,paint);
	        		// left
	        		rectf.top = offset_y + top;
	        		rectf.bottom = offset_y + preview_h - bottom;
	        		rectf.left  = offset_x + left - line_width;
	        		rectf.right = offset_x + left +1;
	        		canvas.drawRect (rectf,paint);
	        		// right
	        		rectf.left  = offset_x + preview_w -right -1;
	        		rectf.right = offset_x + preview_w -right+ line_width;
	        		canvas.drawRect (rectf,paint);
	        	}
        	}
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
	int crop_color = 0;
	Runnable showing_runnable = new Runnable() {
		
		@Override
		public void run() {
			handler.removeCallbacks(showing_runnable);
			handler.postDelayed(showing_runnable,333);
			++show_count;
			
			if( ((show_count/3)&1) == 0 ){
				setBackgroundColor(0xff333333);
			}else{
				setBackgroundColor(0xff444444);
			}

			switch( show_count%6 ){
			case 0: crop_color = 0xffff8800; break;
			case 1: crop_color = 0xff88ff00; break;
			case 2: crop_color = 0xff00ff88; break;
			case 3: crop_color = 0xff0088ff; break;
			case 4: crop_color = 0xff8800ff; break;
			case 5: crop_color = 0xffff0088; break;
			}
			invalidate();
		}
	};
}
