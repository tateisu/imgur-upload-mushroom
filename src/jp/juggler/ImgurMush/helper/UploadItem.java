package jp.juggler.ImgurMush.helper;

import java.io.File;

import android.graphics.Bitmap;

public class UploadItem {
	public File file;
	public String desc;
	public Bitmap preview;
	int w = -1;
	int h = -1;
	
	public UploadItem(BaseActivity act ,String path) {
		file = new File(path);
		updateDesc(act);
	}

	public void updateDesc(BaseActivity act ){
		final String size = TextFormat.formatByteSize(file.length());
		final String mtime = TextFormat.formatTime(act,file.lastModified());
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
