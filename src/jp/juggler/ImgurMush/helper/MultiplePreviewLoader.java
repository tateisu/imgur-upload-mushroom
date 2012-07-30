package jp.juggler.ImgurMush.helper;

import java.io.File;
import java.lang.ref.SoftReference;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import jp.juggler.util.LifeCycleListener;
import jp.juggler.util.LogCategory;
import jp.juggler.util.WorkerBase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;

/*
	画像サムネイルのロードなどに使うローダー
*/
public class MultiplePreviewLoader {
	static final LogCategory log = new LogCategory("MultiplePreviewLoader");
	static final boolean debug = false;

	static class Result{
		Bitmap bitmap;
		int orig_w = -1;
		int orig_h = -1;
	}

	public static interface Callback{
		public void onLoad(int w,int h,Bitmap bitmap);
	}

	static class Request{
		File file;
		boolean measure_only;
		Callback callback;
		int max_w;
		int max_h;

		@Override
		public boolean equals(Object o){
			if( ! (o instanceof Request) ) return false;
			Request b = (Request)o;
			return file.getAbsolutePath().equals(b.file.getAbsolutePath())
				&& measure_only == b.measure_only
				&& max_w == b.max_w
				&& max_h == b.max_h
				;
		}

		@Override
		public int hashCode(){
			return file.getAbsolutePath().hashCode();
		}
	}
	

	static class SoftCache<K,V> {
		ConcurrentHashMap<K,SoftReference<V>> map = new ConcurrentHashMap<K,SoftReference<V>>();
		void put(K key,V value){
			map.put(key,new SoftReference<V>(value));
		}
		V get(K key){
			SoftReference<V> ref = map.get(key);
			return ref==null? null : ref.get();
		}
		void sweepDeadEntry(){
			Iterator<Map.Entry<K,SoftReference<V>>> it = map.entrySet().iterator();
			while(it.hasNext()){
				Map.Entry<K,SoftReference<V>> entry = it.next();
				if( entry.getValue().get() == null ) it.remove();
			}
		}
		void clear(){
			map.clear();
		}
	}
	
	// コンストラクタ
	public MultiplePreviewLoader(
		BaseActivity act
		,boolean bClearAtStart // trueを指定するとActivity.onStart() のタイミングでキューをクリアする
	){
		this.act= act;
		this.ui_handler = act.ui_handler;
		this.bClearAtStart = bClearAtStart;

		act.lifecycle_manager.add(activity_listener);
	}

	// URLのダウンロードを行い、終了したらリスナを呼ぶ。
	// data_typeの指定によってリスナのonData data引数の型が変わる
	public void request(File file,boolean measure_only,int max_w,int max_h,Callback callback){
		Request req = new Request();
		req.file = file;
		req.measure_only = measure_only;
		req.callback = callback;
		req.max_w = max_w;
		req.max_h = max_h;

		// TODO: handle exception
		final Result data = cache.get(req);
		if( data != null ){
			if(debug) log.d("using cache (UI)");
			req.callback.onLoad(data.orig_w,data.orig_h,data.bitmap);
			return;
		}
		try {
			queue.put(req);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		if(worker!=null) worker.notifyEx();
	}

	static final int request_skip_limit_default = 1000 * 60 * 10;

	final BaseActivity act;
	final Handler ui_handler;
	final boolean bClearAtStart;
	final LinkedBlockingQueue<Request> queue = new LinkedBlockingQueue<Request>();
	final SoftCache<Request,Result> cache = new SoftCache<Request, Result>();

	// ミリ秒を指定すると適当な間隔でキャッシュをクリアする
	public int default_expire = 0;

	LifeCycleListener activity_listener = new LifeCycleListener(){
		@Override public void onStart() {
			if( bClearAtStart ){
				queue.clear();
				cache.clear();
			}
			worker = new Worker();
			worker.start();
		}

		@Override public void onStop() {
			if(worker!=null) worker.joinASync(log,"MultiplePreviewLoader");
		}
	};



	Worker worker;
	class Worker extends WorkerBase {
		AtomicBoolean bCancelled = new AtomicBoolean(false);

		@Override
		public void cancel() {
			bCancelled.set(true);
			interrupt();
			notifyEx();
		}

		@Override
		public void run() {
			log.d("worker start");

			// 最後にキャッシュを掃除した時刻
			long last_sweep = 0;

			while(!bCancelled.get()){
				long now = System.currentTimeMillis();

				if( now - last_sweep >= 1000*60*10 ){
					last_sweep = now;
					// メモリ上のキャッシュの掃除
					cache.sweepDeadEntry();
					continue;
				}

				// キューからデータを取り出す
				final Request item = queue.poll();
				if( item == null ){
					waitEx(1000*60*10);
					continue;
				}

				{
					final Result data = cache.get(item);
					if( data != null ){
						if(debug) log.d("using cache (BG)");
						ui_handler.post(new Runnable() {
							@Override
							public void run() {
								if( bCancelled.get() ) return;
								item.callback.onLoad(data.orig_w,data.orig_h,data.bitmap);
							}
						});
						continue;
					}
				}

				if(debug) log.d("load data..");
				
				final Result data = new Result();
				
				try{
					String path = item.file.getPath();
					BitmapFactory.Options options = new BitmapFactory.Options();

					// 画像の大きさだけ取得
					options.inJustDecodeBounds = true;
					options.outWidth =0;
					options.outHeight =0;
					BitmapFactory.decodeFile(path, options);

					int w = options.outWidth;
					int h = options.outHeight;
					if( w < 1 || h < 1 ) throw new RuntimeException("bitmap decode failed.");

					data.orig_w = w;
					data.orig_h = h;
					act.ui_handler.post(new Runnable() {
						@Override
						public void run() {
							if( bCancelled.get() ) return;
							item.callback.onLoad(data.orig_w,data.orig_h,data.bitmap);
						}
					});

					if(! item.measure_only ){
						int sample_size = 1;
						int limit_w = (int)(0.5f + item.max_w * 1.5f);
						int limit_h = (int)(0.5f + item.max_h * 1.5f);
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
						Bitmap image = BitmapFactory.decodeFile(path, options);
						if(image == null ) throw new RuntimeException("bitmap decode failed.");

						log.d("inSampleSize=%d, scaled=%f,%f"
								,options.inSampleSize
								,image.getWidth()/(float)data.orig_w
								,image.getHeight()/(float)data.orig_h
						);

						data.bitmap = image;
					}
				}catch(Throwable ex){
					act.report_ex(ex);
				}

				act.ui_handler.post(new Runnable() {
					@Override
					public void run() {
						if( bCancelled.get() ) return;
						cache.put(item,data);
						item.callback.onLoad(data.orig_w,data.orig_h,data.bitmap);
					}
				});
			}
			log.d("worker end");
		}
	}
}
