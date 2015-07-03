/**
 * Copyright Alex Objelean
 */
package ro.isdc.wro.model.factory;

import ro.isdc.wro.WroRuntimeException;
import ro.isdc.wro.config.ReadOnlyContext;
import ro.isdc.wro.model.group.Inject;

import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;


/**
 * To be used by the implementations which load the model from a resource provided as stream.
 * 
 * @author Alex Objelean
 * @created 9 Aug 2011
 * @since 1.4.0
 */
public abstract class AbstractWroModelFactory
    implements WroModelFactory {
  @Inject
  private ReadOnlyContext context;

  private long resourceTimestamp;
  
  /**
   * Override this method, in order to provide different xml definition file name.
   * 
   * @return stream of the xml representation of the model.
   * @throws IOException
   *           if the stream couldn't be read.
   */
  protected InputStream getModelResourceAsStream()
      throws IOException {
    final ServletContext servletContext = context.getServletContext();
    // Don't allow NPE, throw a more detailed exception
    if (servletContext == null) {
      throw new WroRuntimeException(
          "No servletContext is available. Probably you are running this code outside of the request cycle!");
    }
    final String resourceLocation = getResourceLocation();
    final InputStream stream = servletContext.getResourceAsStream(resourceLocation);
    if (stream == null) {
      throw new IOException("Invalid resource requested: " + resourceLocation);
    }
    return stream;
  }
  
  /**
   * @return the default name of the file describing the wro model.
   */
  protected abstract String getDefaultModelFilename();
  
  /**
   * {@inheritDoc}
   */
  public void destroy() {
  }

  public boolean isExpired() {
    final ServletContext servletContext = context.getServletContext();
    if (servletContext == null) {
      return false;
    }

    final String resourceLocation = getResourceLocation();
    String path = servletContext.getRealPath(resourceLocation);
    if (null == path) {
      return false;
    } else {
      File file = new File(path);

      if (!file.exists()) {
        return false;
      } else {
        long oldTimestamp = this.resourceTimestamp;
        this.resourceTimestamp = file.lastModified();

        return this.resourceTimestamp > oldTimestamp;
      }
    }
  }

  protected String getResourceLocation() {
    return "/WEB-INF/" + getDefaultModelFilename();
  }
}
