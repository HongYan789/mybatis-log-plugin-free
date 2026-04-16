package com.starxg.mybatislog;

import org.junit.Assert;
import org.junit.Test;

import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;

public class MyBatisLogConsoleFilterTest {
    @Test
    public void test() {
        Assert.assertEquals(MyBatisLogConsoleFilter.parseParams("123(String)").element().getKey(), "123");
        Assert.assertEquals(MyBatisLogConsoleFilter.parseParams("123(String)").element().getValue(), "String");

        Assert.assertEquals(MyBatisLogConsoleFilter.parseParams("null, 1(Long), 张三(String), 18(Integer)").size(), 4);
        Assert.assertEquals(MyBatisLogConsoleFilter.parseParams("1(Long), 张三(String), null, 18(Integer)").size(), 4);

        Assert.assertEquals(MyBatisLogConsoleFilter.parseSql("UPDATE mp_user SET name=? WHERE id=? AND name=? AND age=? AND email=? AND deleted=0",
                        MyBatisLogConsoleFilter.parseParams("null, 1(Long), 张三(String), 18(Integer), x@y.com(String)")).toString(),
                "UPDATE mp_user SET name=null WHERE id=1 AND name='张三' AND age=18 AND email='x@y.com' AND deleted=0");

        Assert.assertEquals(MyBatisLogConsoleFilter.parseSql("UPDATE mp_user SET name=? WHERE id=? AND name=?",
                        MyBatisLogConsoleFilter.parseParams("null, null, null")).toString(),
                "UPDATE mp_user SET name=null WHERE id=null AND name=null");
    }

    @Test
    public void testJpaBindingParse() {
        final Map.Entry<Integer, Map.Entry<String, String>> binding =
                MyBatisLogConsoleFilter.parseJpaBinding("2026-01-01 10:00:00 TRACE BasicBinder : binding parameter [2] as [VARCHAR] - [tom]");
        Assert.assertNotNull(binding);
        Assert.assertEquals(2, (int) binding.getKey());
        Assert.assertEquals("tom", binding.getValue().getKey());
        Assert.assertEquals("VARCHAR", binding.getValue().getValue());
    }

    @Test
    public void testJpaSqlParse() {
        final Queue<Map.Entry<String, String>> params = new ArrayDeque<>();
        params.offer(new AbstractMap.SimpleEntry<>("tom", "VARCHAR"));
        params.offer(new AbstractMap.SimpleEntry<>("18", "INTEGER"));
        params.offer(new AbstractMap.SimpleEntry<>("null", "INTEGER"));
        final String sql = MyBatisLogConsoleFilter.parseSql(
                "select * from user where name=? and age>? and score is ?",
                params).toString();
        Assert.assertEquals("select * from user where name='tom' and age>18 and score is null", sql);
    }
}
