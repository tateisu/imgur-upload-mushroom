package jp.juggler.ImgurMush.data;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.TreeSet;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.AbstractHttpEntity;

import android.util.Log;

import jp.juggler.util.Base64;
import jp.juggler.util.CancelChecker;

import oauth.signpost.OAuth;

public class StreamSigner {
	static final String TAG="StreamSigner";

	// パラメータの集合
	TreeSet<Parameter> parameter_set = new TreeSet<Parameter>();

	// 処理中断チェック
	public CancelChecker cancel_checker;


	static class Base64InputStream extends InputStream {
		byte[] tmp1 = new byte[1];
		InputStream in;

		byte[] decoded;
		int decoded_next =0;

		byte[] plain = new byte[ 600 ];
		int plain_left = 0;

		Base64InputStream(InputStream in ){
			this.in = in;
		}
		@Override
		public void close() throws IOException {
			in.close();
		}

		@Override
		public int read() throws IOException {
			int n = read(tmp1,0,1);
			return n == 1 ? (tmp1[0]&255) : -1 ;
		}

		@Override
		public int read(byte[] b) throws IOException {
			return read( b,0,b.length);
		}

		@Override
		public int read(byte[] buffer, int offset, int length) throws IOException {
			int nRead = 0;
			while( nRead < length ){
				if( decoded != null && decoded_next < decoded.length ){
					buffer[ offset++] = decoded[ decoded_next++];
					++nRead;
					continue;
				}

				int delta = in.read( plain,plain_left, plain.length - plain_left );
				if( delta == -1 ){
					if( plain_left == 0 ) return -1;
					decoded_next = 0;
					decoded = Base64.encode(plain,0,plain_left,Base64.NO_WRAP);
					plain_left = 0;
				}else{
					plain_left += delta;
					int tail = plain_left % 3;
					int block = plain_left - tail;
					decoded_next = 0;
					decoded = Base64.encode(plain,0,block,Base64.NO_WRAP);
					for(int i=0;i<tail;++i){
						plain[i] = plain[block+i];
					}
					plain_left = tail;
				}
			}
			return nRead > 0 ? nRead: -1;
		}
	}


	// %HH エンコードを行うストリームラッパー
	static class EncodedInputStream extends InputStream {
		static byte[] hexchar = new byte[16];
		static{
			String s = "0123456789ABCDEF";
			for(int i=0;i<16;++i){
				hexchar[i] = (byte)s.charAt(i);
			}
		}

		byte[] tmp = new byte[2];
		int tmp_size =0;
		int tmp_next =0;
		InputStream in;
		//
		long nRead = 0;
		Parameter p;
		boolean bName;

		EncodedInputStream(Parameter parameter, boolean bName){
			this.p = parameter;
			this.bName = bName;
			this.in = bName ? p.getName() : p.getValue();
		}
		EncodedInputStream(InputStream in){
			this.in = in;
		}
		@Override
		public void close() throws IOException {
			in.close();
			if( p !=null ){
				if(bName){
					p.name_encode_length = nRead;
				}else{
					p.value_encode_length = nRead;
				}
			}
		}

		@Override
		public int available() throws IOException {
			if( tmp_next < tmp_size ) return 1;
			return in.available();
		}

		@Override
		public int read() throws IOException {
			if( tmp_next < tmp_size ){
				++nRead;
				return tmp[ tmp_next++];
			}
			int c = in.read();
			if( c < 0 ) return c;
			++nRead;

			if( c == '-'
			||  c == '.'
			||  c == '_'
			||  c == '~'
			||  ( c >= '0' && c <= '9' )
			||  ( c >= 'A' && c <= 'Z' )
			||  ( c >= 'a' && c <= 'z' )
			){
				return c;
			}else{
				tmp_size = 2;
				tmp_next = 0;
				tmp[0] = hexchar[ (c>>4)&15 ];
				tmp[1] = hexchar[ (c)&15 ];
				return '%';
			}
		}
	}

	public abstract class Parameter implements Comparable<Parameter>{
		public long value_encode_length = -1;
		public long name_encode_length = -1;
		public boolean isPost;
		public byte[] name;

		@Override
		public boolean equals(Object o) {
			if( o instanceof Parameter) return 0== compareTo((Parameter)o);
			return false;
		}

		@Override
		public int compareTo(Parameter b) {
			// どんなパラメータもnullよりは大きい
			if(b==null) return 1;

			try{
				// 名前の比較
				int v = compareStream( new EncodedInputStream(getName()), new EncodedInputStream(b.getName()) );

				// 値の比較
				if(v==0) v = compareStream( new EncodedInputStream(getValue()), new EncodedInputStream(b.getValue()) );

				return v;
			}catch(IOException ex){
				throw new RuntimeException(ex);
			}
		}

		public InputStream getName(){
			return new ByteArrayInputStream( name );
		}

		public abstract InputStream getValue();

		public long getEncodeLength() throws IOException{
			if( value_encode_length == -1 ) value_encode_length = getStreamLength(new EncodedInputStream(getValue()));
			if( name_encode_length  == -1 ) name_encode_length  = getStreamLength(new EncodedInputStream(getName()));
			return name_encode_length + value_encode_length + 1;
		}

		@Override
		public int hashCode() {
			// equals をいじったので、これもオーバライドしないと警告が出る
			return super.hashCode();
		}
	}

	public class ByteArrayParameter extends Parameter{
		byte[] value;

		@Override
		public InputStream getValue() {
			return new ByteArrayInputStream(value);
		}

		public ByteArrayParameter(boolean isPost,byte[] name,byte[] value){
			this.isPost = isPost;
			this.name = name;
			this.value = value;
		}
		public ByteArrayParameter(boolean isPost,String name,String value){
			this.isPost = isPost;
			this.name = utf8_encode(name);
			this.value = utf8_encode(value);
		}
	}

	public class FileParameter extends Parameter{
		File file;
		boolean isBase64;


		public FileParameter(boolean isPost,byte[] name,File file,boolean isBase64){
			this.isPost = isPost;
			this.name = name;
			this.file = file;
			this.isBase64 = isBase64;
		}
		public FileParameter(boolean isPost,String name,File file,boolean isBase64){
			this.isPost = isPost;
			this.name = utf8_encode(name);
			this.file = file;
			this.isBase64 = isBase64;
		}
		@Override
		public InputStream getValue() {
			try {
				if( isBase64 ){
					return new Base64InputStream(new FileInputStream(file));
				}else{
					return new BufferedInputStream(new FileInputStream(file));
				}
			} catch (FileNotFoundException ex) {
				throw new RuntimeException(ex);
			}
		}

	}

	////////////////////////////////////////////////
	// パラメータセットを元にoAuthの署名を計算する

	public String hmac_sha1(String consumer_secret,String token_secret,String request_method,String url) {
		try {
			long start = System.currentTimeMillis();

			// 署名オブジェクトのキー
			ByteArrayOutputStream bao = new ByteArrayOutputStream();
			copyStream( bao , getEscapedStream(consumer_secret ));
			bao.write( (byte) '&' );
			copyStream( bao , getEscapedStream(token_secret ));
			byte[] keyBytes = bao.toByteArray();

			// 署名計算オブジェクトを初期化
			String MAC_NAME = "HmacSHA1";
			Mac mac = Mac.getInstance(MAC_NAME);
			mac.init(new SecretKeySpec(keyBytes, MAC_NAME));
			//	Signature Base String をスキャン
			scan_stream( mac,getEscapedStream(request_method));
			mac.update( (byte)'&' );
			scan_stream( mac,getEscapedStream(normalize_url(url)));
			mac.update( (byte)'&' );
			int n=0;
			byte[] and_escaped = "%26".getBytes("UTF-8");
			byte[] eq_escaped  = "%3D".getBytes("UTF-8");
			for( Parameter item : parameter_set ){
				if(n++ > 0 ) mac.update( and_escaped );
				scan_stream( mac,new EncodedInputStream( new EncodedInputStream( item,true )));
				mac.update( eq_escaped );
				scan_stream( mac,new EncodedInputStream( new EncodedInputStream( item,false)));
			}
			Log.d(TAG,"hmac_sha1 time="+(System.currentTimeMillis() - start));
			return new String( Base64.encode(mac.doFinal(),Base64.NO_WRAP));
		}catch(Throwable ex){
			throw new RuntimeException(ex.getMessage(),ex);
		}
	}

	///////////////////////////////////////////////////////////////////////////
	// HTTPヘッダにoAuthの署名を追加する

	public void sign_header( HttpRequestBase request ,String oauth_signature) throws IOException{
		StringBuilder sb = new StringBuilder();

		sb.append("OAuth ");
		follow_header( sb , "realm");
		follow_header( sb , "oauth_token");
		follow_header( sb , "oauth_consumer_key");
		follow_header( sb , "oauth_version");
		follow_header( sb , "oauth_signature_method");
		follow_header( sb , "oauth_timestamp");
		follow_header( sb , "oauth_nonce");

		if( oauth_signature != null ){
			Parameter p = new ByteArrayParameter(false,"oauth_signature",oauth_signature);
			follow_header( sb , p);
		}

		request.setHeader(OAuth.HTTP_AUTHORIZATION_HEADER, sb.toString());
	}

	/////////////////////////////////////////////////////////////////
	// パラメータの一部をPOSTリクエストのメッセージボディとして送信できるようにする

	public HttpEntity createPostEntity() throws IOException{
		return new PostEntity();
	}

	class PostEntity extends AbstractHttpEntity{
		long length;
		static final boolean debug = false;

		PostEntity() throws IOException {
			setContentType("application/x-www-form-urlencoded");

			long start = System.currentTimeMillis();
			length = 0;
			for( Parameter item: parameter_set ){
				if( cancel_checker.isCancelled() ) throw new RuntimeException("Cancelled.");
				if( item.isPost ){
					if( length!= 0 ) length += 1;
					length += item.getEncodeLength();
				}
			}
			long t = System.currentTimeMillis() - start;
			Log.d(TAG,"PostEntity length="+length+", time="+t);
		}

		@Override
		public boolean isRepeatable() {
			return true;
		}

		@Override
		public boolean isStreaming() {
			return false;
		}

		@Override
		public void writeTo(OutputStream outstream) throws IOException {
			copyStream(outstream,getContent());
		}

		@Override
		public long getContentLength() {
			return length;
		}

		@Override
		public InputStream getContent() throws IOException,IllegalStateException {
			return new InputStream(){
				Iterator<Parameter> iterator = parameter_set.iterator();
				Parameter parameter;
				InputStream stream = null;
				int mode = 0;
				boolean bFirst = true;

				@Override
				public void close() throws IOException {
					if(stream != null ) stream.close();
				}

				@Override
				public int read() throws IOException {
					for(;;){
						if( mode == 0 ){
							if( !iterator.hasNext() ) return -1;
							parameter = iterator.next();
							if( ! parameter.isPost ) continue;
							stream = new EncodedInputStream( parameter.getName() );
							mode = 1;
							if( bFirst ){
								bFirst = false;
							}else{
								return '&';
							}
						}
						if( mode == 1 ){
							int c = stream.read();
							if( c != -1 ) return c;
							stream.close();
							stream = new EncodedInputStream( parameter.getValue() );
							mode = 2;
							return '=';
						}
						if( mode == 2 ){
							int c = stream.read();
							if( c != -1 ) return c;
							stream.close();
							stream=null;
							mode = 0;
						}
					}
				}

				@Override
				public int read(byte[] buffer, int start, int length) throws IOException {
					if( cancel_checker != null && cancel_checker.isCancelled() ) throw new RuntimeException("Cancelled.");

					int p = start;
					int end = start+length;
					while( p <  end ){
						int c = read();
						if( c == -1 ) break;
						buffer[p++] = (byte)c;
					}
					return p == start ? -1 : p-start;
				}

				@Override
				public int read(byte[] b) throws IOException {
					return read(b,0,b.length);
				}

			};
		}
	}

	////////////////////////////////////////////////////////////////////////////
	// 内部用ユーティリティ

	void addParam(boolean isPost,byte[] name,byte[] value){
		parameter_set.add( new ByteArrayParameter(isPost,name,value) );
	}
	public void addParam(boolean isPost,String name,String value){
		parameter_set.add( new ByteArrayParameter(isPost,name,value) );
	}
	public void addParam(boolean isPost,String name,File value,boolean isBase64){
		parameter_set.add( new FileParameter(isPost,name,value,isBase64) );
	}

	Parameter find_parameter( String name_str ){
		byte[] name = utf8_encode(name_str);
		for( Parameter item : parameter_set.tailSet(new ByteArrayParameter(false,name,new byte[0])) ){
			if( Arrays.equals(name, item.name) ) return item;
			break;
		}
		return null;
	}

	void follow_header( StringBuilder sb ,Parameter p ) throws IOException{
		if( sb.length() > 6 ) sb.append(", ");
		sb.append( utf8_decode( p.name ));
		sb.append( "=\"" );
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		copyStream(bao, p.getValue() );
		sb.append( utf8_decode(bao.toByteArray() ) );
		sb.append( "\"" );
	}

	void follow_header( StringBuilder sb ,String key ) throws IOException{
		Parameter p = find_parameter(key);
		if( p != null ) follow_header(sb,p );
	}

	////////////////////////////////////////////////////////////////////////////
	// 内部用ユーティリティ

	static final byte[] utf8_encode(String s){
		try {
			return s.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			// 発生しない
			return null;
		}
	}

	static final String utf8_decode(byte[] b){
		try {
			return new String(b,"UTF-8");
		} catch (UnsupportedEncodingException e) {
			// 発生しない
			return null;
		}
	}

	static final int array_compare(byte[] a,byte[] b){
		for(int i=0;;++i){
			if( a.length >= i ){
				return  b.length >= i ? 0 : -1;
			}else if( b.length >= i ){
				return 1;
			}
			int n =  (a[i]&255) - (b[i]&255);
			if( n != 0 ) return n;
		}
	}

	static final int compareStream(InputStream in_a,InputStream in_b) throws IOException{
		try{
			for(;;){
				int a = in_a.read();
				int b = in_b.read();
				if( a== -1 && b==-1 ) return 0;
				if( a<b ) return -1;
				if( b<a ) return 1;
			}
		}finally{
			in_a.close();
			in_b.close();
		}
	}

	static final long getStreamLength(InputStream in) throws IOException{
		long n= 0;
		for(;;){
			if(in.read()==-1) break;
			++n;
		}
		in.close();
		return n;
	}

	static final void copyStream(OutputStream out,InputStream in) throws IOException{
		byte[] tmp = new byte[1024];
		for(;;){
			int delta = in.read(tmp);
			if(delta < 0 ) break;
			out.write(tmp,0,delta);
		}
	}

	static InputStream getEscapedStream( String s ){
		return new EncodedInputStream(new ByteArrayInputStream(utf8_encode(s)));
	}

	void scan_stream(Mac mac,InputStream in) throws IOException{
		final int tmp_length = 1024;
		byte[] tmp = new byte[tmp_length];
		int tmp_next = 0;
		for(;;){
			if( tmp_next >= tmp_length ){
				mac.update(tmp,0,tmp_next);
				tmp_next = 0;
				if( cancel_checker !=null && cancel_checker.isCancelled() ) throw new RuntimeException("Cancelled.");
			}
			int c = in.read();
			if(c<0) break;
			tmp[tmp_next++] = (byte)c;
		}
		if(tmp_next > 0 ) mac.update(tmp,0,tmp_next);
	}

	static String normalize_url(String url) throws URISyntaxException {
		URI uri = new URI(url);
		String scheme = uri.getScheme().toLowerCase();
		String authority = uri.getAuthority().toLowerCase();
		boolean dropPort = (scheme.equals("http") && uri.getPort() == 80)
				|| (scheme.equals("https") && uri.getPort() == 443);
		if (dropPort) {
			// find the last : in the authority
			int index = authority.lastIndexOf(":");
			if (index >= 0) {
				authority = authority.substring(0, index);
			}
		}
		String path = uri.getRawPath();
		if (path == null || path.length() <= 0) {
			path = "/"; // conforms to RFC 2616 section 3.2.2
		}
		// we know that there is no query and no fragment here.
		return scheme + "://" + authority + path;
	}
}
