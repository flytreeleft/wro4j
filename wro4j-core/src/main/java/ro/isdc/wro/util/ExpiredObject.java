/*
 * Copyright (c) 2015 Crazydan.org
 * All rights reserved.
 */

package ro.isdc.wro.util;

/**
 * Expired object, when object is expired, it should be refresh
 *
 * @author <a href="mailto:flytreeleft@126.com">flytreeleft</a>
 * @date 2015-07-01
 */
public interface ExpiredObject {
  /**
   * Whether the object is expired or not
   */
  boolean isExpired();
}
