package cn.cheney.test;

import android.util.Log;

import java.util.HashMap;

import cn.cheney.xrouter.annotation.XMethod;
import cn.cheney.xrouter.annotation.XParam;
import cn.cheney.xrouter.annotation.XRoute;
import cn.cheney.xrouter.core.RouteCallback;
import cn.cheney.xrouter.core.method.IMethod;

@XRoute(module = "home")
public class TestMethod implements IMethod {

    private static final String TAG = TestMethod.class.getSimpleName();

    @XMethod(name = "/getBookName")
    public static Book getBookName(@XParam(name = "book") Book book) {
        Log.i(TAG, "getBookName  =" + book);
        return book;
    }


    @XMethod(name = "/getAsyncBookName")
    public static void getAsyncBookName(@XParam(name = "book") Book book, RouteCallback callback) {
        Log.i(TAG, "getBookName  =" + book);
        HashMap<String, Object> map = new HashMap<>();
        map.put("book", book);
        callback.onResult(map);
    }


}

