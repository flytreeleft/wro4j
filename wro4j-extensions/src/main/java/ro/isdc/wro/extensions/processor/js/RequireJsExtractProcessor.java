/*
 * Copyright (c) 2015 Crazydan.org
 * All rights reserved.
 */

package ro.isdc.wro.extensions.processor.js;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ro.isdc.wro.WroRuntimeException;
import ro.isdc.wro.config.ReadOnlyContext;
import ro.isdc.wro.extensions.processor.support.requirejs.RequireJsExtractor;
import ro.isdc.wro.model.group.Inject;
import ro.isdc.wro.model.resource.Resource;
import ro.isdc.wro.model.resource.ResourceType;
import ro.isdc.wro.model.resource.SupportedResourceType;
import ro.isdc.wro.model.resource.processor.Destroyable;
import ro.isdc.wro.model.resource.processor.ResourcePostProcessor;
import ro.isdc.wro.model.resource.processor.ResourcePreProcessor;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;


/**
 * Extract module ID and dependencies from require js file
 *
 * @author <a href="mailto:flytreeleft@126.com">flytreeleft</a>
 * @date 2015-06-10
 * @see RequireJsExtractor
 */
@SupportedResourceType(ResourceType.JS)
public class RequireJsExtractProcessor
    implements ResourcePostProcessor, ResourcePreProcessor, Destroyable {
  private static final Logger LOG = LoggerFactory.getLogger(RequireJsExtractProcessor.class);

  public static final String ALIAS = "requireJsExtract";

  @Inject
  private ReadOnlyContext context;

  @Override
  public void process(Reader reader, Writer writer)
      throws IOException {
    throw new WroRuntimeException("This processor: " + getClass().getSimpleName() + " cannot work as a postProcessor!");
  }

  @Override
  public void process(Resource resource, Reader reader, Writer writer)
      throws IOException {
    String content = IOUtils.toString(reader);

    try {
      String jsUri = null == resource ? "" : resource.getUri();
      RequireJsExtractor extractor = new RequireJsExtractor(jsUri, content);

      writer.write(extractor.extract());
    } catch (Exception e) {
      onException(e);
    } finally {
      reader.close();
      writer.close();
    }
  }

  @Override
  public void destroy()
      throws Exception {

  }

  /**
   * Invoked when an exception occurs during processing. Default implementation wraps the exception into
   * {@link WroRuntimeException} and throws it further.
   *
   * @param e {@link Exception} thrown during processing.
   */
  protected void onException(final Exception e) {
    throw WroRuntimeException.wrap(e);
  }
}
