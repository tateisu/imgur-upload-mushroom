#!/usr/bin/perl --
use strict;
use warnings;

sub decode_tabstop{
	my($tabwidth,$text)=@_;
	my $a = "";
	my $ie = length($text);
	for(my $i=0;$i<$ie;++$i){
		my $c = substr($text,$i,1);
		if( $c eq "\t" ){
			my $cur = length($a)%$tabwidth;
			if($cur == 0 ){
				$a .= " "x$tabwidth;
			}else{
				$a .= " "x($tabwidth-$cur);
			}
		}else{
			$a .= $c;
		}
	}
	return $a;
}
sub encode_tabstop{
	my($tabwidth,$text)=@_;
	
	my $end = length($text);
	my $i=0;
	my $a = "";
	while($i < $end ){
		my $part = substr($text,$i,$tabwidth);
		my $n = length($part);
		$i += $n;
		if( $n >= $tabwidth ){
			if( $part =~ s/( +)$// ){
				$a .= $part . "\t";
				next;
			}
		}
		$a .= $part;
	}
	return $a;
}

my $tabwidth = shift;
my $infile = shift;

$infile or die "usage: $0 tabwidth file\n";
my $tmpfile = "$infile.tmp";

eval{
	my $in;
	my $out;
	open($in,"<",$infile) or die "file error: $infile $!";
	open($out,">",$tmpfile) or die "file error: $tmpfile $!";
	my $changed = 0;
	while(<$in>){
		s/\x0a//g;
		# 改行コードの補正
		s/\x0d//g and $changed=1;
		# (空白以外が存在する行に限り)行末の空白を削除
		/\S/ and s/\s+$// and $changed = 1;
		# 行頭の空白を整形
		s/^([ \t\/\#]*)//;
		my $old_indent = $1;
		my $new_indent = encode_tabstop($tabwidth,decode_tabstop($tabwidth,$old_indent));
		( $old_indent ne $new_indent ) and $changed = 1;
		print $out $new_indent,$_,"\x0a";
	}
	close($in) or die "read error: $infile $!";
	close($out)  or die "write error: $tmpfile $!";
	# 変更があった場合は入力ファイルをrename
	if( $changed){
		rename($tmpfile,$infile) or die "rename failed: $infile $!";
		warn "updated: $infile\n";
	}
};
$@ and warn $@;
unlink $tmpfile;
