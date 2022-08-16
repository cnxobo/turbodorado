package org.xobo.dorado.turbo.core.db.mybatis;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import com.bstek.dorado.data.provider.Page;
import com.github.pagehelper.PageRowBounds;


@Intercepts({
    @Signature(type = Executor.class, method = "query",
        args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
    @Signature(type = Executor.class, method = "query",
        args = {MappedStatement.class, Object.class, RowBounds.class,
            ResultHandler.class, CacheKey.class, BoundSql.class}),})
public class DoradoPageInterceptor implements Interceptor {

  private static Pattern pattern = Pattern.compile("^param\\d+$");

  /**
   *
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  public Object intercept(Invocation invocation) throws Throwable {
    Object[] args = invocation.getArgs();

    // 方法所有参数
    Object parameter = args[1];

    Page<?> page = null;

    if (parameter instanceof Page) {
      page = (Page<?>) parameter;
    } else if (parameter instanceof Map) {
      Map<String, Object> parameterMap = (Map<String, Object>) parameter;
      Map params = null;
      int mapParams = 0;
      boolean hasPage = false;
      for (Entry<String, Object> entry : parameterMap.entrySet()) {
        Object value = entry.getValue();
        if (value instanceof Page) {
          page = (Page) value;
          hasPage = true;
        } else if (value instanceof Map) {
          String key = entry.getKey();
          Matcher matcher = pattern.matcher(key);

          if (matcher.find()) {
            mapParams++;
            if (mapParams == 1) {
              params = (Map) value;
            }
          }

        }
      }

      // 如果只有一个Map参数(query(page, paramsMap) 形式，那么把paramsMap参数提升到上层Map)，
      if (hasPage && mapParams == 1) {
        parameterMap.putAll(params);
      }
    }

    PageRowBounds pageRowBounds = null;
    if (page != null) {
      pageRowBounds =
          new PageRowBounds(page.getPageSize() * (page.getPageNo() - 1), page.getPageSize());
      pageRowBounds.setCount(true);
      args[2] = pageRowBounds;
    }

    Object value = invocation.proceed();

    if (page != null) {
      page.setEntities((Collection) value);
      page.setEntityCount(pageRowBounds.getTotal().intValue());
    }

    return value;
  }

  public Object plugin(Object target) {
    if (target instanceof Executor) {
      return Plugin.wrap(target, this);
    } else {
      return target;
    }
  }

  public void setProperties(Properties properties) {

  }

}
