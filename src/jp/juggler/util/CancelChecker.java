package jp.juggler.util;

//! 呼び出し元のオブジェクトに対してバックグラウンド処理がキャンセルされたかどうかを確認するためのインタフェース. 
//! HTTPClient 等,ワーカースレッドの下で動作する処理から使用される。 
public interface CancelChecker {
	boolean isCancelled();
}
