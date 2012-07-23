package jp.juggler.ImgurMush;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import jp.juggler.util.LifeCycleListener;
import jp.juggler.util.LogCategory;
import jp.juggler.util.TextUtil;
import jp.juggler.util.WorkerBase;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;

/*
	画像サムネイルのロードなどに使うローダー
*/
public class DataLoader {
	static final LogCategory log = new LogCategory("DataLoader");
	static final boolean debug = false;
	
	public static abstract class Listener<T>{
		public abstract void onData(File file,T data);
		public void onError(String msg){}
		public void onProgress(int size,int read){}
	}

	// コンストラクタ
	public DataLoader(
		BaseActivity act
		,boolean bClearAtResume	// trueを指定するとActivity.onStart() のタイミングでキューをクリアする
		,boolean bUseCacheDir 	// trueを指定すると Context.getCacheDir() に指定したフォルダにキャッシュディレクトリを作成する
		,String prefix			// 作成するキャッシュディレクトリのファイル名
	){
		this.act= act;
		this.ui_handler = act.ui_handler;
		this.bClearAtResume = bClearAtResume;
		this.cache_dir = cachedir_init(act,bUseCacheDir,prefix);

		act.lifecycle_manager.add(activity_listener);
	}
	
	public static final int DATATYPE_BYTES =0;
	public static final int DATATYPE_BITMAP =1;
	public static final int DATATYPE_UTF8 =2;
	public static final int DATATYPE_FILE=3; // キャッシュにファイルがある場合はonDataのdata引数がnullになるかもしれない
	static final int request_skip_limit_default = 1000 * 60 * 10;
	
	// URLのダウンロードを行い、終了したらリスナを呼ぶ。
	// data_typeの指定によってリスナのonData data引数の型が変わる
	public void request(String url,int data_type,int request_skip_limit, Listener<?> listener){
		Item item = new Item();
		item.url = url;
		item.file = new File(cache_dir,TextUtil.url2name( url));
		item.data_type = data_type;
		item.request_skip_limit = request_skip_limit;
		item.listener = listener;
		final Object result = cache.get(item);
		if( result != null ){
			if(debug) log.d("using cache (UI)");
			item.fireCallback(result);
			return;
		}
		try {
			queue.put(item);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		if(worker!=null) worker.notifyEx();
	}
	
	final Context act;
	final Handler ui_handler;
	final boolean bClearAtResume;
	final File cache_dir;
	final LinkedBlockingQueue<Item> queue = new LinkedBlockingQueue<Item>();
	final SoftCache<Item,Object> cache = new SoftCache<Item, Object>();

	// ミリ秒を指定すると適当な間隔でキャッシュをクリアする
	public int default_expire = 0; 
	
	LifeCycleListener activity_listener = new LifeCycleListener(){
		@Override public void onStart() {
			if( bClearAtResume ) queue.clear();
			worker = new Worker();
			worker.start();
		}

		@Override public void onStop() {
			if(worker!=null) worker.joinASync(log,"DataLoader");
		}
	};

	static class Item{
		String url;
		File file;
		int data_type;
		long request_skip_limit;
		Listener<?> listener;
		
		@Override
		public boolean equals(Object o){
			if( ! (o instanceof Item) ) return false;
			Item b = (Item)o; 
			return (url.equals(b.url) && data_type == b.data_type );
		}
		@Override
		public int hashCode(){
			return url.hashCode() ^ data_type;
		}
		@SuppressWarnings("unchecked")
		public void fireCallback(Object _result) {
			try{
				if( data_type == DATATYPE_BITMAP  ){
					((Listener<Bitmap>)listener).onData(file,(Bitmap)_result);
				}else if( data_type == DATATYPE_UTF8 ){
					((Listener<String>)listener).onData(file,(String)_result);
				}else{
					((Listener<byte[]>)listener).onData(file,(byte[])_result);
				}
			}catch(Throwable ex){
				ex.printStackTrace();
			}
			
		}
	};
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
	}
	
	Worker worker;
	class Worker extends WorkerBase {
		AtomicBoolean bCancelled = new AtomicBoolean(false);

		@Override
		public void cancel() {
			bCancelled.set(true);
			interrupt();
			notifyEx();
		}
		
		@SuppressWarnings("null")
		@Override
		public void run() {
			log.d("worker start");
			
			// 最後にキャッシュを掃除した時刻
			long last_sweep = 0;

			while(!bCancelled.get()){
				long now = System.currentTimeMillis();
				if( now - last_sweep >= 1000*60*10 ){
					last_sweep = now;

					// 自動sweepが有効なら、適当な周期でキャッシュフォルダを掃除する
					if( default_expire > 0 ) cachedir_sweep( default_expire );
					
					// メモリ上のキャッシュの掃除
					cache.sweepDeadEntry();
					continue;
				}
				
				// キューからデータを取り出す
				final Item item = queue.poll();
				if( item == null ){
					waitEx(1000*60*10);
					continue;
				}
				
				final Object cached_data = cache.get(item);
				if( cached_data != null ){
					if(debug) log.d("using cache (BG)");
					ui_handler.post(new Runnable() {
						@Override
						public void run() {
							if( bCancelled.get() ) return;
							item.fireCallback(cached_data);
						}
					});
					continue;
				}
				
				if(debug) log.d("load data..");
				byte[] data = null;
				String last_error = null;
				boolean bOK = false;
				String url = item.url;
				for(int nTry=0;nTry<10;++nTry){
					if( bCancelled.get() ) break;
					try{
						long file_mtime = item.file.lastModified();
						if( item.file.exists() ){
							// 更新時刻と現在との間が request_skip_limit 以内ならHTTPリクエストは不要とみなす
							long delta = System.currentTimeMillis() - file_mtime;
							if( delta <= item.request_skip_limit ){
								bOK = true;
								break;
							}
						}
						HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
						conn.setConnectTimeout(1000 * 3);
						conn.setReadTimeout(1000*3);
						if( file_mtime != 0L ) conn.setIfModifiedSince(item.file.lastModified());
						conn.connect();
						int rcode = conn.getResponseCode();
						if( rcode == 304 ){
							if( item.file.exists() ){
								bOK=true;
								break;
							}
						}else if( rcode == 200 || rcode==302){
							InputStream in = conn.getInputStream();
							try{
								int capa = conn.getContentLength();
								if( capa < 4096) capa = 4096;
								ByteArrayOutputStream bao = new ByteArrayOutputStream( capa );
								byte[] tmp = new byte[4096];
								for(;;){
									int delta = in.read(tmp,0,tmp.length);
									if( delta <= 0 ) break;
									bao.write(tmp,0,delta);
								}
								data = bao.toByteArray();
								try{
									File tmp_path = new File(item.file.getPath()+".tmp");
									FileOutputStream out = new FileOutputStream(tmp_path);
									try{
										out.write(data);
									}finally{
										out.close();
									}
									long data_mtime = conn.getLastModified();
									if(data_mtime <= 0 ) data_mtime = System.currentTimeMillis();
									if(! tmp_path.setLastModified(data_mtime)
									|| ! tmp_path.renameTo(item.file)
									){
										throw new RuntimeException("cannot set metadata to file:"+item.file);
									}
									bOK = true;
									break;
								}catch(Throwable ex){
									ex.printStackTrace();
									if(item.file.exists() ) item.file.delete();
									last_error =String.format("%s %s",ex.getClass().getSimpleName(),ex.getMessage());
								}
							}finally{
								in.close();
							}
							break;
						}
						log.e("http error: %d %s",rcode,item.url);
						if( rcode >= 400 && rcode < 500 ){
							last_error =String.format("HTTP error %d",rcode);
							break;
						}
						// retry ?
					}catch(MalformedURLException ex){
						ex.printStackTrace();
						last_error =String.format("bad URL:%s",ex.getMessage());
						break;
					}catch(IOException ex){
						ex.printStackTrace();
						last_error =String.format("%s %s",ex.getClass().getSimpleName(),ex.getMessage());
						continue;
					}
				}
				
				// ここでitemはnullのはずがないんだが、なぜか警告が出る…

				// キャッシュが有効ならファイルから読む
				if( bOK && item.data_type != DATATYPE_FILE && data == null ){
					try{
						FileInputStream fis = new FileInputStream(item.file);
						try{
							ByteArrayOutputStream bao = new ByteArrayOutputStream( fis.available() );
							byte[] tmp = new byte[4096];
							for(;;){
								int delta = fis.read(tmp,0,tmp.length);
								if( delta <= 0 ) break;
								bao.write(tmp,0,delta);
							}
							data = bao.toByteArray();
						}finally{
							fis.close();
						}
					}catch(IOException ex){
						bOK = false;
						ex.printStackTrace();
						last_error =String.format("%s %s",ex.getClass().getSimpleName(),ex.getMessage());
					}
				}
				
				Object result = data;
				
				if( bOK && item.data_type == DATATYPE_BITMAP ){
					try{
						result = decodePurgeableBitmap(data);
						if( result == null ){
							item.file.delete();
							throw new RuntimeException("bitmap decode failed.");
						}
					}catch(Throwable ex){
						bOK = false;
						ex.printStackTrace();
						last_error =String.format("%s %s",ex.getClass().getSimpleName(),ex.getMessage());
					}
				}
				if( bOK && item.data_type == DATATYPE_UTF8){
					try{
						result = TextUtil.decodeUTF8(data);
						if( result == null ){
							item.file.delete();
							throw new RuntimeException("text decode failed.");
						}
					}catch(Throwable ex){
						bOK = false;
						ex.printStackTrace();
						last_error =String.format("%s %s",ex.getClass().getSimpleName(),ex.getMessage());
					}
				}
				if( !bOK ){
					final String e = last_error;
					ui_handler.post(new Runnable() {
						@Override
						public void run() {
							if( bCancelled.get() ) return;
							try{
								item.listener.onError(e);
							}catch(Throwable ex){
								ex.printStackTrace();
							}
						}
					});
				}else{
					final Object _result = result;
					ui_handler.post(new Runnable() {
						@Override
						public void run() {
							if( bCancelled.get() ) return;
							cache.put(item,_result);
							item.fireCallback(_result);
						}
					});
				}
			}
			log.d("worker end");
		}
	}
	
	////////////////////////////////////////////////////
	// キャッシュファイルの管理

	// キャッシュフォルダを作成
	private static File cachedir_init(BaseActivity act,boolean bUseCacheDir,String prefix){
		File dir = new File( ( bUseCacheDir ? act.getCacheDir() : act.getFilesDir() )  ,prefix);
			// getCacheDir が指すフォルダはシステムが勝手に削除することがある。
			// getFilesDir が指すフォルダはシステムが勝手に削除することは…ないと思う

		// キャッシュフォルダを作成
		if(! dir.mkdirs() && !dir.isDirectory() ){
			throw new RuntimeException("cannot make cache directory "+dir.toString() );
		}
		return dir;
	}
	
	// 古いキャッシュを破棄
	public void cachedir_sweep(long mtime_expire ){
		try{
			long now = System.currentTimeMillis();
			String[] list = cache_dir.list();
			if( list != null ){
				for( String entry : list ){
					File f = new File(cache_dir,entry);
					if( f.isFile() && now - f.lastModified() >= mtime_expire ){
						f.delete();
						log.d("delete %s",f.toString());
					}
				}
			}
		}catch(Throwable ex){
			ex.printStackTrace();
		}
	}

	////////////////////////////////////////////////////
	// bitmapのデコード

	private static BitmapFactory.Options purgeable_option;
	public static Bitmap decodePurgeableBitmap(byte[] data){
		BitmapFactory.Options option = purgeable_option;
		if( option == null ){
			option = new BitmapFactory.Options();
			option.inPurgeable = true;
			purgeable_option = option;
		}
		return BitmapFactory.decodeByteArray(data,0,data.length,option);
	}
//	public static void replaceImageViewBitmap(ImageView iv,Bitmap bitmap){
//		if( iv != null ){
//			Drawable d = iv.getDrawable();
//			iv.setImageBitmap(bitmap);
//			if( d != null && d instanceof BitmapDrawable ){
//				BitmapDrawable bd = ((BitmapDrawable)d);
//				Bitmap b = bd.getBitmap();
//				if(b!=null) b.recycle();
//			}
//		}
//	}
//	public static void clearImageView(ImageView iv){
//		if( iv != null ){
//			Drawable d = iv.getDrawable();
//			if( d != null && d instanceof BitmapDrawable ){
//				iv.setImageDrawable(new ColorDrawable(0));
//				BitmapDrawable bd = ((BitmapDrawable)d);
//				Bitmap b = bd.getBitmap();
//				if(b!=null) b.recycle();
//			}
//		}
//	}
}
