/*
 * Copyright (c) 2015 Crazydan.org
 * All rights reserved.
 */

package ro.isdc.wro.extensions.model.factory;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import ro.isdc.wro.WroRuntimeException;
import ro.isdc.wro.config.ReadOnlyContext;
import ro.isdc.wro.model.WroModel;
import ro.isdc.wro.model.factory.AbstractWroModelFactory;
import ro.isdc.wro.model.factory.WroModelFactory;
import ro.isdc.wro.model.factory.XmlModelFactory;
import ro.isdc.wro.model.group.Inject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;


/**
 * The model factory scanning classpath xml to create WroModel object
 * <pre>
 *     <filter>
 *         <filter-name>WebResourceOptimizer</filter-name>
 *         <filter-class>ro.isdc.wro.http.WroFilter</filter-class>
 *         <init-param>
 *             <param-name>modelDefinitionFile</param-name>
 *             <param-value>classpath:/wro.xml,classpath*:/wro4j.xml</param-value>
 *         </init-param>
 *     </filter>
 * </pre>
 *
 * @author <a href="mailto:flytreeleft@126.com">flytreeleft</a>
 * @date 2015-07-01
 */
public class ClasspathXmlModelFactory
    extends AbstractWroModelFactory {
  private static final Logger LOG = LoggerFactory.getLogger(ClasspathXmlModelFactory.class);

  public static final String ALIAS = "classpath";

  private static final String DEFAULT_CLASSPATH_RESOURCE = "classpath:/wro.xml";

  @Inject
  private ReadOnlyContext context;

  private Map<Resource, Long> configurationTimestampMap = new HashMap<Resource, Long>();

  @Override
  protected String getDefaultModelFilename() {
    String modelDefinitionFile = context.getConfig().getModelDefnitionFile();

    return StringUtils.isNotBlank(modelDefinitionFile) ? modelDefinitionFile : DEFAULT_CLASSPATH_RESOURCE;
  }

  public WroModel create() {
    configurationTimestampMap.clear();

    WroModel model = new WroModel();
    Map<Resource, InputStream> resourceStreamMap = getModelResourceStreams();
    for (Map.Entry<Resource, InputStream> entry : resourceStreamMap.entrySet()) {
      Resource resource = entry.getKey();
      LOG.debug("Parsing resource: {} ...", resource);

      WroModelFactory factory = createModelFactory(entry.getValue());

      model.merge(factory.create());
      configurationTimestampMap.put(resource, getLastModified(resource));
    }

    return model;
  }

  @Override
  public boolean isExpired() {
    for (Map.Entry<Resource, Long> entry : configurationTimestampMap.entrySet()) {
      Resource resource = entry.getKey();
      Long oldTimestamp = entry.getValue();

      if (oldTimestamp < getLastModified(resource)) {
        return true;
      }
    }

    return false;
  }

  protected WroModelFactory createModelFactory(InputStream input) {
    WroModelFactory factory = new InputStreamXmlModelFactory(input);

    return factory;
  }

  protected Map<Resource, InputStream> getModelResourceStreams() {
    Map<Resource, InputStream> resourceStreamMap = new HashMap<Resource, InputStream>();
    String[] resourcePatterns = getDefaultModelFilename().split(",");
    PathMatchingResourcePatternResolver pathResolver = new PathMatchingResourcePatternResolver();

    for (String resourcePattern : resourcePatterns) {
      try {
        Resource[] resources = pathResolver.getResources(resourcePattern);

        if (null != resources && resources.length > 0) {
          for (Resource resource : resources) {
            resourceStreamMap.put(resource, resource.getInputStream());
          }
        }
      } catch (IOException e) {
        throw new WroRuntimeException("Error while loading XML resource: " + e.getMessage(), e);
      }
    }

    return resourceStreamMap;
  }

  protected long getLastModified(Resource resource) {
    try {
      return resource.exists() ? resource.lastModified() : -1;
    } catch (IOException e) {
      return -1;
    }
  }

  /**
   * The model factory for parsing xml stream
   */
  protected static class InputStreamXmlModelFactory
      extends XmlModelFactory {
    private InputStream input;

    public InputStreamXmlModelFactory(InputStream input) {
      this.input = input;
    }

    @Override
    protected InputStream getModelResourceAsStream()
        throws IOException {
      return input;
    }
  }
}
