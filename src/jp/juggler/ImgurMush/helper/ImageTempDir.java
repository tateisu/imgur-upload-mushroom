package jp.juggler.ImgurMush.helper;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

import jp.juggler.ImgurMush.PrefKey;
import jp.juggler.ImgurMush.R;
import jp.juggler.util.HelperEnv;
import jp.juggler.util.LogCategory;
import android.content.SharedPreferences;
import android.os.Environment;

public class ImageTempDir {
	static final LogCategory log = new LogCategory("ImageTempDir");

	public static File getTempDir(final HelperEnv eh ){
		String path = eh.pref().getString(PrefKey.KEY_TEMP_DIR,null);
		if(path !=null && path.length() > 0){
			File dir = new File( path );
			if( dir.mkdirs() || dir.isDirectory() ){
				if( dir.canWrite() ) return dir;
				log.e("not writeable: %s",dir.getPath());
				// ディレクトリはあるが書き込みできない
			}else{
				// ディレクトリを作成できない。ディレクトリではない。
				log.e("not directory: %s",dir.getPath());
			}
		}
		// 初期化が必要なら初期化する
		try{
			File ext = Environment.getExternalStorageDirectory();
			if( !ext.isDirectory() ){
				eh.show_toast(true,R.string.storage_not_directory);
				return null;
			}
			if( !ext.canWrite() ){
				eh.show_toast(true,R.string.storage_not_writable);
				return null;
			}
			for(int i=1;i<100;++i){
				String dirname="ImgurMushroom"+(i<2?"":i);
				File dir = new File(ext,dirname);
				if( dir.mkdirs() || dir.isDirectory() ){
					if(! dir.canWrite() ){
						log.e("not writeable: %s",dir.getPath());
						continue;
					}
					new File(dir,".nomedia").setLastModified(System.currentTimeMillis());
					path = dir.getAbsolutePath();
					SharedPreferences.Editor e = eh.pref().edit();
					e.putString(PrefKey.KEY_TEMP_DIR,path);
					e.commit();
					log.d("initialize image dir: %s",path);
					return dir;
				}else{
					log.e("not directory: %s",dir.getPath());
				}
			}
		}catch(Throwable ex){
			ex.printStackTrace();
		}
		return null; //
	}

	static Random r = new Random();

	public static File makeTempFile(HelperEnv eh){
		File dir = getTempDir(eh);
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
				eh.show_toast(true,R.string.file_temp_error);
				return null;
			}
		}

	}

}
