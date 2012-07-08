package jp.juggler.ImgurMush;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.LinkedBlockingQueue;

import jp.juggler.util.LogCategory;
import jp.juggler.util.WorkerBase;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.widget.ImageView;
import android.widget.TextView;

public class DataLoader {
	public static abstract class Listener{
		public  abstract void onData(File file,byte[] data);
		public void onError(String msg){}
		public void onProgress(int size,int read){}
	}

	static final LogCategory log = new LogCategory("DataLoader");
	Context context;
	Handler ui_handler;
	LinkedBlockingQueue<Item> queue = new LinkedBlockingQueue<Item>();
	
	File cache_dir;
	
	// true にするとキャッシュにあるデータはbyte配列に読まずにonDataを呼び出す
	public boolean no_cache_load = false;

	// ミリ秒を指定すると適当な間隔でキャッシュをクリアする
	public int default_expire = 0; 
	
	public DataLoader(Context context,boolean bCacheDir,String prefix){
		this.context= context;
		ui_handler = new Handler();
		// キャッシュディレクトリ
		if( bCacheDir ){
			cache_dir = new File(context.getCacheDir(),prefix);
			// getCacheDir が指すフォルダはシステムが勝手に削除することがある。
		}else{
			cache_dir = new File(context.getFilesDir(),prefix);
			// getFilesDir が指すフォルダはシステムが勝手に削除することは…ないと思う
		}
		if(! cache_dir.mkdir() && !cache_dir.isDirectory() )
			throw new RuntimeException("cannot make cache directory "+cache_dir.toString() );
	}
	public void reset() {
		if(worker!=null) worker.joinLoop(new LogCategory("DataLoader"),"DataLoader");
		queue.clear();
		worker = new Worker();
		worker.start();
	}
	
	public void stop(){
		if(worker!=null) worker.joinLoop(new LogCategory("DataLoader"),"DataLoader");
	}
	
	public void sweep(long expire ){
		try{
			long now = System.currentTimeMillis();
			String[] list = cache_dir.list();
			if( list != null ){
				for( String entry : list ){
					File f = new File(cache_dir,entry);
					if( f.isFile() && now - f.lastModified() >= expire ){
						f.delete();
						log.d("delete %s",f.toString());
					}
				}
			}
		}catch(Throwable ex){
			ex.printStackTrace();
		}
	}
	
	public static byte[] encodeUTF8(String str){
		try{
			return str.getBytes("UTF-8");
		}catch(Throwable ex){
			return null; // 入力がnullの場合のみ発生
		}
	}
	public static String decodeUTF8(byte[] data){
		try{
			return new String(data,"UTF-8");
		}catch(Throwable ex){
			return null; // 入力がnullの場合のみ発生
		}
	}
	static final char[] hex = new char[]{ '0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f' };
	public static final void addHex(StringBuilder sb,byte b){
		sb.append( hex[ (b>>4)&15] );
		sb.append( hex[ (b   )&15] );
	}
	public static String url2name(String url){
		StringBuilder sb = new StringBuilder();
		byte[] b = encodeUTF8(url);
		for(int i=0,ie=b.length;i<ie;++i){
			addHex(sb,b[i]);
		}
		return sb.toString();
	}
	
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
	public static void clearImageView(ImageView iv){
		if( iv != null ){
			Drawable d = iv.getDrawable();
			if( d != null && d instanceof BitmapDrawable ){
				iv.setImageDrawable(new ColorDrawable(0));
				BitmapDrawable bd = ((BitmapDrawable)d);
				Bitmap b = bd.getBitmap();
				if(b!=null) b.recycle();
			}
		}
	}

	public void request(String url,Listener listener){
	//	log.d("request %s",url);
		Item item = new Item();
		item.url = url;
		item.listener = listener;
		item.file = new File(cache_dir,url2name( url));
		try {
			queue.put(item);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		if(worker!=null) worker.notifyEx();
		
	}

	public void requestForImageView(String url,final ImageView iv){
		if(url == null || iv == null ) return;
		request(url , new DataLoader.Listener(){
			@Override
			public void onData(File file,byte[] data) {
				Bitmap b = decodePurgeableBitmap(data);
				if(b!=null){
					clearImageView(iv);
					iv.setImageBitmap(b);
				}
			}
		});
	}
	public void requestForTextview(String url,final TextView tv){
		if(url == null || tv == null ) return;
		request(url,new DataLoader.Listener() {
			@Override
			public void onData(File file,byte[] data) {
				tv.setText(decodeUTF8(data));
			}
		});
	}
	
	static class Item{
		String url;
		Listener listener;
		File file;

	};
	
	
	Worker worker;
	class Worker extends WorkerBase {
		volatile boolean bCancelled;

		@Override
		public void cancel() {
			bCancelled = true;
			interrupt();
			notifyEx();
		}
		
		@Override
		public void run() {
			log.d("worker start");
			
			// 古いファイルを消す
			long last_sweep = 0;

			while(!bCancelled){
				long now = System.currentTimeMillis();
				if( now - last_sweep >= 1000*60*10 && default_expire > 0){
					last_sweep = now;
					sweep( default_expire );
				}
				
				final Item item = queue.peek();
				if( item == null ){
			//		log.d("sleep..");
					waitEx(1000*86400);
					continue;
				}
			//	log.d("start %s",item.url);

				
				byte[] data = null;
				String last_error = null;
				boolean bOK = false;
				for(int nTry=0;nTry<10;++nTry){
					if( bCancelled ) break;
					try{
						if( item.file.exists() ){
							if( System.currentTimeMillis() - item.file.lastModified() < 1000 ){
								if( no_cache_load ){
									bOK = true;
									break;
								}else{
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
										bOK = true;
										break;
									}finally{
										fis.close();
									}
								}
							}
						}
						
						HttpURLConnection conn = (HttpURLConnection) new URL(item.url).openConnection();
						conn.setConnectTimeout(1000 * 3);
						conn.setReadTimeout(1000*3);
						if(item.file.exists() ) conn.setIfModifiedSince(item.file.lastModified());
						conn.connect();
						int rcode = conn.getResponseCode();
						
						if( rcode == 304 ){
							if( item.file.exists() ) item.file.setLastModified(now);

							if( no_cache_load ){
								bOK = true;
							}else{
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
									bOK = true;
								}finally{
									fis.close();
								}
							}
							break;
						}
						if( rcode == 200 ){
							InputStream in = conn.getInputStream();
							try{
								ByteArrayOutputStream bao = new ByteArrayOutputStream(  );
								byte[] tmp = new byte[4096];
								for(;;){
									int delta = in.read(tmp,0,tmp.length);
									if( delta <= 0 ) break;
									bao.write(tmp,0,delta);
								}
								data = bao.toByteArray();
								if( data != null ){
									try{
										FileOutputStream out = new FileOutputStream(item.file);
										try{
											out.write(data);
										}finally{
											out.close();
										}
										if( item.file.exists() ) item.file.setLastModified(now);
										bOK = true;
									}catch(Throwable ex){
										ex.printStackTrace();
										if(item.file.exists() ) item.file.delete();
										last_error =String.format("%s %s",ex.getClass().getSimpleName(),ex.getMessage());
									}
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
				if( !bOK ){
					final String e = last_error;
					ui_handler.post(new Runnable() {
						@Override
						public void run() {
							if(bCancelled) return;
							try{
								item.listener.onError(e);
							}catch(Throwable ex){
								ex.printStackTrace();
							}
						}
					});
				}else{
					final byte[] _data = data;
					ui_handler.post(new Runnable() {
						@Override
						public void run() {
							if(bCancelled) return;
							try{
								item.listener.onData(item.file,_data);
							}catch(Throwable ex){
								ex.printStackTrace();
							}
						}
					});
				}
				queue.poll();
			}
			log.d("worker end");
		}
	}

}
