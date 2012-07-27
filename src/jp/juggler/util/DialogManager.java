package jp.juggler.util;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedList;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;

/*
	Activity中のDialogのライフサイクルの管理を行う。
	Activity標準のモノと異なり、ダイアログの再利用やリストアを行わない前提。

	できることは次の３つだけ。
	- 内部リストに登録してから表示する
	- 内部リストに登録されたダイアログのいずれかが表示中か調べる
	- 内部リストに登録された全ダイアログを閉じる。 Activity#onDestroy() までに呼ばれるべき。
*/

public class DialogManager {

	////////////////////////////////////////////
	// ライフサイクルイベント

	// 画面のonCreateから呼ぶこと
	public DialogManager(Activity act) {
		this.act = act;
	}

	// 画面のonDestroy()から呼ぶこと。登録された全てのダイアログを閉じる。
	public void onDestroy(){
		dismiss_all_dialog();
	}

	////////////////////////////////////////////
	// ユーティリティ


	// ダイアログを表示して、内部リストに控えておく
	public void show_dialog( AlertDialog.Builder dialog) {
		show_dialog(dialog.create());
	}

	// ダイアログを表示して、内部リストに控えておく
	public void show_dialog(Dialog dialog){
		// リスト中の破棄された要素を掃除する
		Iterator<WeakReference<Dialog>> it = dialog_list.iterator();
		while( it.hasNext() ){
			Dialog d = it.next().get();
			if( d==null || !d.isShowing() ) it.remove();
		}

		// リストにダイアログを追加する
		dialog_list.add(new WeakReference<Dialog>(dialog));

		// ダイアログを表示する
		dialog.show();
	}

	// ダイアログが表示中なら真を返す
	public boolean isShowing(){
		Iterator<WeakReference<Dialog>> it = dialog_list.iterator();
		while( it.hasNext() ){
			Dialog d = it.next().get();
			if( d==null || !d.isShowing() ){
				it.remove();
				continue;
			}
			return true;
		}
		return false;
	}

	// 表示中の全てのダイアログを閉じる
	public void dismiss_all_dialog(){
		Iterator<WeakReference<Dialog>> it = dialog_list.iterator();
		while( it.hasNext() ){
			Dialog d = it.next().get();
			if( d!=null && d.isShowing() ) d.dismiss();
			it.remove();
		}
	}

	////////////////////////////////////////////

	final Activity act;
	final LinkedList<WeakReference<Dialog>> dialog_list = new  LinkedList<WeakReference<Dialog>>();
}
