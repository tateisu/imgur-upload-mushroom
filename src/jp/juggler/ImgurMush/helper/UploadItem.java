package jp.juggler.ImgurMush.helper;

import java.io.File;

import jp.juggler.util.HelperEnv;

import android.graphics.Bitmap;

public class UploadItem {
	public File file;
	public String desc;
	public Bitmap preview;
	int w = -1;
	int h = -1;
	
	public UploadItem(HelperEnv env ,String path) {
		file = new File(path);
		updateDesc(env);
	}

	public void updateDesc(HelperEnv env){
		final String size = TextFormat.formatByteSize(file.length());
		final String mtime = TextFormat.formatTime(env.context,file.lastModified());
		if( w <= 0 ){
			desc = String.format(
				"%s %s\n%s"
				,size
				,mtime
				,file.getAbsolutePath()
			);
		}else{
			desc = String.format(
				"%s %sx%spx %s\n%s"
				,size
				,w,h
				,mtime
				,file.getAbsolutePath()
			);
		}
	}
}
