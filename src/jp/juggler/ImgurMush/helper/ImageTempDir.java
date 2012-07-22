package jp.juggler.ImgurMush.helper;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

import jp.juggler.ImgurMush.BaseActivity;
import jp.juggler.ImgurMush.PrefKey;
import jp.juggler.util.LogCategory;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Environment;

public class ImageTempDir {
	static final LogCategory log = new LogCategory("ImageTempDir");
	
	public static File getTempDir(Activity act,SharedPreferences pref) {
		String path = pref.getString(PrefKey.KEY_TEMP_DIR,null);
		if(path !=null && path.length() > 0){
			File dir = new File( pref.getString(PrefKey.KEY_TEMP_DIR,null));
			if( dir.mkdirs() || dir.isDirectory() ){
				if( dir.canWrite() ) return dir;
				// ディレクトリはあるが書き込みできない
			}else{
				// ディレクトリを作成できない。ディレクトリではない。
			}
		}
		// 初期化が必要なら初期化する
		try{
			File ext = Environment.getExternalStorageDirectory();
			for(int i=1;i<100;++i){
				String dirname="ImgurMushroom"+(i<2?"":i);
				File dir = new File(ext,dirname);
				if( dir.mkdirs() || dir.isDirectory() ){
					if(! dir.canWrite() ) continue;
					new File(dir,".nomedia").setLastModified(System.currentTimeMillis());
					path = dir.getAbsolutePath();
					SharedPreferences.Editor e = pref.edit();
					e.putString(PrefKey.KEY_TEMP_DIR,path);
					e.commit();
					return dir;
				}
			}
		}catch(Throwable ex){
			ex.printStackTrace();
		}
		return null; // 
	}
	
	static Random r = new Random();
	
	public static File makeTempFile(BaseActivity act){
		File dir = getTempDir(act,act.pref());
		if(dir==null) return null;
		
		for(;;){
			File file = new File (dir,String.format("%x.jpg",r.nextInt()));
			if( file.isFile() || file.isDirectory() ) continue;
			try{
				FileOutputStream fos = new FileOutputStream(file);
				fos.close();
				return file;
			}catch(Throwable ex){
				log.e("cannot create temp file %s",file.getPath());
				return null;
			}
		}
		
	}

}
