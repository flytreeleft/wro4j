/*
 * Copyright (c) 2015 Crazydan.org
 * All rights reserved.
 */

package ro.isdc.wro.extensions.processor.support.requirejs;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Extractor for getting module ID and dependencies of require js file
 * Assume module definition was：<pre>
 *     define(function(require, exports, module) {
 *         var A = require('a');
 *         var B = require('./b');
 *         var C = require('./../c');
 *     });
 * </pre>
 * then will be changed as：<pre>
 *     define('path/to/module', ['a', 'path/to/b', 'path/c'], function(require, exports, module) {
 *         var A = require('a');
 *         var B = require('./b');
 *         var C = require('./../c');
 *     } );
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@126.com">flytreeleft</a>
 * @date 2015-06-10
 */
public class RequireJsExtractor {
  /**
   * regex for module definition: \bdefine\s*\(\s*function\s*\(
   */
  public static final String MOD_DEF_REGEX = "\\bdefine\\s*\\(\\s*function\\s*\\(";
  /**
   * regex for module definition: \bdefine\s*\(\s*function\s*\(
   */
  public static final Pattern
      MOD_DEF_PATTERN =
      Pattern.compile(MOD_DEF_REGEX, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
  // regex for dependency reference: \brequire\s*\(\s*('|")\s*([^'"]+)\s*\1\s*\)
  /**
   * regex for dependency reference: "(?:\\"|[^"])*"|'(?:\\'|[^'])*'|\/\*[\S\s]*?\*\/|\/(?:\\\/|[^\/\r\n])+\/(?=[^\/])|\/\/.*|\.\s*require|(?:^|[^$])\brequire\s*\(\s*(["'])(.+?)\1\s*\)
   */
  public static final String
      DEP_REF_REGEX =
      "\"(?:\\\\\"|[^\"])*\"|'(?:\\\\'|[^'])*'|\\/\\*[\\S\\s]*?\\*\\/|\\/(?:\\\\\\/|[^\\/\\r\\n])+\\/(?=[^\\/])|\\/\\/.*|\\.\\s*require|(?:^|[^$])\\brequire\\s*\\(\\s*([\"'])(.+?)\\1\\s*\\)";
  /**
   * regex for dependency reference: "(?:\\"|[^"])*"|'(?:\\'|[^'])*'|\/\*[\S\s]*?\*\/|\/(?:\\\/|[^\/\r\n])+\/(?=[^\/])|\/\/.*|\.\s*require|(?:^|[^$])\brequire\s*\(\s*(["'])(.+?)\1\s*\)
   */
  public static final Pattern
      DEP_REF_PATTERN =
      Pattern.compile(DEP_REF_REGEX, Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

  private String uri;
  private String content;

  public RequireJsExtractor(String uri, String content) {
    this.uri = uri.replaceFirst("^/+", "");
    this.content = content;
  }

  /**
   * @return extracted module js
   */
  public String extract() {
    String extractedContent = this.content;

    if (MOD_DEF_PATTERN.matcher(extractedContent).find()) {
      List<String> deps = extractDependencies(extractedContent);
      String parent = this.uri.substring(0, this.uri.lastIndexOf("/") + 1);
      StringBuffer sb = new StringBuffer("define('" + this.uri.substring(0, this.uri.length() - 3) + "', [");

      for (int i = 0, len = deps.size(); i < len; i++) {
        String dep = deps.get(i);
        String dir = parent;
        // relative to absolute
        if (!dir.isEmpty() && !dep.startsWith("/") && (dep.startsWith("./") || dep.startsWith("../"))) {
          while (dep.startsWith("./")) {
            dep = dep.substring(2);
          }
          while (dep.startsWith("../") && !dir.isEmpty()) {
            dep = dep.substring(3);
            dir = dir.replaceFirst("([^/]+)/$", "");
          }
          dep = dir + dep;
        }
        if (i > 0) {
          sb.append(", ");
        }
        sb.append("'" + dep + "'");
      }
      sb.append("], function(");

      extractedContent = extractedContent.replaceFirst(MOD_DEF_REGEX, sb.toString());
    }

    return extractedContent;
  }

  protected List<String> extractDependencies(String content) {
    List<String> deps = new ArrayList<String>();
    String[] requireParams = parseRequireParameter(content).split("\\s");

    for (String param : requireParams) {
      if (StringUtils.isNotBlank(param)) {
        deps.add(param.trim());
      }
    }

    return deps;
  }

  /**
   * extract <code>require(moduleId)</code> argument, get dependency module ID
   */
  protected String parseRequireParameter(String source) {
    StringBuffer result = new StringBuffer();
    ReadingState state = ReadingState.NORMAL;
    String require = "require";
    boolean isRequireParam = false;
    int index = 0;
    int length = source.length();

    while (index < length) {
      char ch = source.charAt(index);
      String head = substr(source, index, 2);

      switch (state) {
        case NORMAL:
          if ('\'' == ch || '"' == ch) {
            state = ReadingState.STRING;
          } else if ("//".equals(head)) {
            state = ReadingState.SINGLE_LINE_COMMENT;
          } else if ("/*".equals(head)) {
            state = ReadingState.MULTI_LINE_COMMENT;
          } else if (require.equals(substr(source, index, require.length()))) {
            state = ReadingState.REQUIRE;
            index += require.length();
          } else {
            index++;
          }
          break;
        case SINGLE_LINE_COMMENT:
          // ignore single line comment
          while (index < length && !(ch == '\n' || ch == '\r')) {
            ch = source.charAt(index);
            index++;
          }
          state = ReadingState.NORMAL;
          break;
        case MULTI_LINE_COMMENT:
          // ignore multi-line comment
          while (index < length && !"*/".equals(head)) {
            head = substr(source, index, 2);
            index++;
          }
          index++; // escape "/" tail
          state = ReadingState.NORMAL;
          break;
        case STRING:
          char startChar = ch; // store start mark: ' or "
          boolean stop = false;
          while (++index < length && !stop) {
            ch = source.charAt(index);
            // stop searching when ch == startChar
            stop = ch == startChar && ('\\' != source.charAt(index - 1) || "\\\\".equals(substr(source, index - 2, 2)));

            if (!stop && isRequireParam) {
              result.append(ch);
            }
          }
          if (isRequireParam) {
            result.append(' ');
            isRequireParam = false;
          }
          state = ReadingState.NORMAL;
          break;
        case REQUIRE:
          while ((' ' == ch || '\n' == ch || '\r' == ch || '\t' == ch || '(' == ch) && ++index < length) {
            ch = source.charAt(index);
          }
          if ('\'' == ch || '"' == ch) {
            state = ReadingState.STRING;
            isRequireParam = true;
          } else {
            state = ReadingState.NORMAL;
          }
          break;
        default:
      }
    }

    return result.toString().trim();
  }

  /**
   * get specified length string from <code>start</code> index
   */
  protected String substr(String sb, int start, int length) {
    int end = start + length;

    if (end > sb.length()) {
      end = sb.length();
    }

    return sb.substring(start < 0 ? 0 : start, end);
  }

  /**
   * Reading State
   */
  enum ReadingState {
    /**
     * normal state
     */
    NORMAL,
    /**
     * a single line comment: //
     */
    SINGLE_LINE_COMMENT,
    /**
     * multi-line comment: /&ast;...&ast;/
     */
    MULTI_LINE_COMMENT,
    /**
     * a sring
     */
    STRING,
    /**
     * keyword：require
     */
    REQUIRE
  }
}
