package jp.juggler.ImgurMush;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


import org.apache.http.Header;
import org.apache.http.HttpEntity;

public class ProgressHTTPEntity implements HttpEntity{
	
	static abstract class ProgressListener{
		abstract void onProgress(long v,long size);
	}
	
	HttpEntity src;
	ProgressListener listener;
	long length;
	
	public ProgressHTTPEntity(HttpEntity src,ProgressListener listener){
		this.src = src;
		this.listener = listener;
		this.length = src.getContentLength();
	}
	
	void fire_progress(long v){
		if(listener!=null) listener.onProgress(v,length);
	}
	
	
	class MyInputStream extends InputStream {
		InputStream in;
    	long n;
		public MyInputStream(InputStream in){
			this.in = in;
		}
		@Override
		public int read() throws IOException {
			int b = in.read();
			if( b >= 0 ){ ++n; fire_progress(n); }
			return b;
		}
		@Override
		public int read (byte[] buffer, int offset, int length) throws IndexOutOfBoundsException,IOException{
			int delta  = in.read(buffer,offset,length);
			if( delta >= 0 ){ n += delta; fire_progress(n); }
			return delta;
		}
		@Override
		public int available() throws IOException {
			return in.available();
		}
		@Override
		public void close() throws IOException {
			in.close();
		}
		
		@Override
		public void mark(int readlimit) {
			in.mark(readlimit);
		}
		
		@Override
		public boolean markSupported() {
			return in.markSupported();
		}
		
		@Override
		public int read(byte[] b) throws IOException {
			int delta  = in.read(b);
			if( delta >= 0 ){ n += delta; fire_progress(n); }
			return delta;			
		}
		
		@Override
		public synchronized void reset() throws IOException {
			in.reset();
		}
		@Override
		public long skip(long byteCount) throws IOException {
			long delta = in.skip(byteCount);
			if( delta >= 0 ){ n += delta; fire_progress(n); }
			return delta;
		}
	}
	

	@Override
	public void consumeContent() throws IOException {
		src.consumeContent();
	}
	@Override
	public InputStream getContent() throws IOException, IllegalStateException {
		return new MyInputStream( src.getContent() );
	}
	@Override
	public Header getContentEncoding() {
		return src.getContentEncoding();
	}
	@Override
	public long getContentLength() {
		return length;
	}
	@Override
	public Header getContentType() {
		return src.getContentType();
	}
	@Override
	public boolean isChunked() {
		return src.isChunked();
	}
	@Override
	public boolean isRepeatable() {
		return src.isRepeatable();
	}
	@Override
	public boolean isStreaming() {
		return src.isStreaming();
	}
	@Override
	public void writeTo(OutputStream outstream) throws IOException {
		InputStream in = src.getContent();
		long n = 0;
		byte[] tmp = new byte[4096];
		for(;;){
			int delta = in.read(tmp,0,tmp.length);
			if( delta <= 0 ) break;
			outstream.write(tmp,0,delta);
			n += delta;
			fire_progress( n );
		}
	}
}