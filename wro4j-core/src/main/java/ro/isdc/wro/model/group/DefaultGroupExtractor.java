/*
 * Copyright (c) 2009.
 */
package ro.isdc.wro.model.group;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ro.isdc.wro.config.Context;
import ro.isdc.wro.model.resource.ResourceType;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Default implementation capable of extracting a single group from the request.
 *
 * @author Alex Objelean
 * @created Created on Nov 3, 2008
 */
public class DefaultGroupExtractor
  implements GroupExtractor {
  private static final Logger LOG = LoggerFactory.getLogger(DefaultGroupExtractor.class);
  /**
   * The name of the attribute where the servlet path is stored when requestDispatcher.include is called.
   */
  public static final String ATTR_INCLUDE_PATH = "javax.servlet.include.servlet_path";
  /**
   * The name of the parameter used to decide if the group must be minimized.
   */
  public static final String PARAM_MINIMIZE = "minimize";

  /**
   * {@inheritDoc}
   */
  public String getGroupName(final HttpServletRequest request) {
    Validate.notNull(request);

    String groupName = null;
    if (needToConcat(request)) {
      // use hash code as group name
      String[] resources = splitConcatResources(request);
      // sort resource to make sure the same concat resources has the same hash code
      Arrays.sort(resources);

      int hashCode = StringUtils.join(resources).hashCode();
      groupName = Integer.toHexString(hashCode);
    } else {
      String uri = request.getRequestURI();
      // check if include or uri path are present and use one of these as request uri.
      String includeUriPath = (String) request.getAttribute(ATTR_INCLUDE_PATH);
      uri = includeUriPath != null ? includeUriPath : uri;

      if (Context.isContextSet() && Context.get().getConfig().isUseURIAsGroupName()) {
        String ctx = request.getContextPath();
        uri = uri.substring(ctx.length());

        groupName = FilenameUtils.removeExtension(stripSessionID(uri));
      } else {
        groupName = FilenameUtils.getBaseName(stripSessionID(uri));
      }
    }

    return StringUtils.isEmpty(groupName) ? null : groupName;
  }

  /**
   * Extracts the resource type, by parsing the uri & finds the extension. If extension is valid ('css' or 'js'),
   * returns corresponding ResourceType, otherwise throws exception.
   * <p>
   * Valid examples of uri are: <code>/context/somePath/test.js</code> or <code>/context/somePath/test.css</code>
   * {@inheritDoc}
   */
  public ResourceType getResourceType(final HttpServletRequest request) {
    Validate.notNull(request);
    final String uri = request.getRequestURI();
    Validate.notNull(uri);

    ResourceType type = null;
    if (needToConcat(request)) {
      String[] resources = splitConcatResources(request);

      if (resources.length > 0) {
        type = getResourceType(resources[0]);
      }
    } else {
      try {
        type = ResourceType.get(FilenameUtils.getExtension(stripSessionID(uri)));
      } catch (final IllegalArgumentException e) {
        LOG.debug("[FAIL] Cannot identify resourceType for uri: {}", uri);
      }
    }

    return type;
  }

  /**
   * The uri is cleaned up (the ;jsessionID is removed).
   * @return the extension of the resource.
   */
  private String stripSessionID(final String uri) {
    return uri.replaceFirst("(?i)(;jsessionid.*)", "");
  }

  /**
   * {@inheritDoc}
   */
  public String encodeGroupUrl(final String groupName, final ResourceType resourceType, final boolean minimize) {
    return String.format("%s.%s?" + PARAM_MINIMIZE + "=%s", groupName, resourceType.name().toLowerCase(), minimize);
  }

  /**
   * The minimization is can be switched off only in debug mode.
   *
   * @return false if the request contains parameter {@link DefaultGroupExtractor#PARAM_MINIMIZE} with value false,
   *         otherwise returns true.
   */
  public boolean isMinimized(final HttpServletRequest request) {
    Validate.notNull(request);
    final String minimizeAsString = request.getParameter(PARAM_MINIMIZE);
    return !(Context.get().getConfig().isDebug() && "false".equalsIgnoreCase(minimizeAsString));
  }

  public boolean needToConcat(HttpServletRequest request) {
    if (request == null || !Context.isContextSet()) {
      return false;
    }

    String concatUriSuffix = Context.get().getConfig().getResourceConcatUriSuffix();

    return StringUtils.isNotBlank(concatUriSuffix)
              && null != request.getRequestURI()
              && request.getRequestURI().endsWith(concatUriSuffix)
              && !request.getParameterMap().isEmpty();
  }

  public String[] splitConcatResources(HttpServletRequest request) {
    String concatSpliter = Context.get().getConfig().getResourceConcatSplitter();
    String[] resources = (String[]) request.getParameterMap().keySet().toArray(new String[] {});

    if (!"&".equals(concatSpliter)) {
      for (String resource : resources) {
        // accept the first one, ignore others
        if (resource.contains(".js") || resource.contains(".css")) {
          resources = resource.split(concatSpliter);
          break;
        }
      }
    }

    if (null != resources) {
      List<String> resourceList = new ArrayList<String>();
      // /${ctx}/xx/aa/concat.cjc or /${ctx}/concat.cjc
      String ctx = request.getContextPath();
      // /xx/aa/concat.cjc or /concat.cjc
      String uri = request.getRequestURI().substring(ctx.length());
      // /xx/aa/ or /
      String parent = uri.substring(0, uri.lastIndexOf('/') + 1);

      for (int i = 0, len = resources.length; i < len; i++) {
        String resource = resources[i];

        if (null != resource && null != getResourceType(resource)) {
          resourceList.add(parent + resource);
        }
      }

      resources = resourceList.toArray(new String[] {});
    }

    return resources;
  }

  protected ResourceType getResourceType(String resource) {
    ResourceType type = null;

    if (resource.endsWith(".js")) {
      type = ResourceType.JS;
    } else if (resource.endsWith(".css")) {
      type = ResourceType.CSS;
    }

    return type;
  }
}
