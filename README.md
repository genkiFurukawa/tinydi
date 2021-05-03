# tiny di
[作って理解するDIコンテナ](https://nowokay.hatenablog.com/entry/20160406/1459918560)を写経しつつ、メタプログラミングなどを学ぶ

## 写経の目的
1. 業務でSpringBootを使っているが、特に理解せずアノテーションを使っているため、理解を深めたい
1. DIコンテナの役割は把握しているが、どのような実装か知らないので理解を深めたい

## 本家との変更点
- パッケージの構成を変更。それに伴って`Context.autoRegister()`のロジックを変更
    - Mainクラス配下にあるクラスを探して、typesに登録するようにした
- `Context.getBean()`で`computeIfAbsent`を使うと`ConcurrentModificationException`が出てしまったので、ベタ書きに修正

## 勉強になったこと
- DIコンテナの仕組み
- アノテーションの使い方（取得方法）
    ```java
    Inject inject = field.getAnnotation(Inject.class);
    ```
- `type.getDeclaredFields()`でクラスのフィールドを取得できること（クラスのメタ情報を扱う方法）
- Springで`@Service`の定義を追いかけて行った時に`@Named`というアノテーションにたどり着いたが、`@Named`はDIコンテナにinjectするという意味を表すアノテーションであること
    - Foo.javaやBar.javaで使用した
- アノテーションをつけてメソッドの機能を拡張するときはjavaAssistを使ってバイナリの操作をする
    - @InvokeLog・@Scopedで実装
- 本当にこれ「DIコンテナというのは、Javaのリフレクションやバイトコード操作、ThreadLocalといった、あまり美しくない部分を覆い隠してきれいなコードでアプリケーションを構築するための仕組みということがわかります。」

## ToDo
- ~~[作って理解するWebフレームワーク - きしだのHatena](https://nowokay.hatenablog.com/entry/20160419/1461032474)の写経~~

## ここではやらないが勉強しておいた方が良いと思ったこと
- エラーについて