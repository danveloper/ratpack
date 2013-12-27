/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ratpack.http.internal;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.net.HttpHeaders;
import ratpack.handling.Context;
import ratpack.handling.Handler;
import ratpack.http.MediaType;
import ratpack.path.PathBinder;
import ratpack.path.PathBinding;

import java.util.List;

public class PathContentTypeHandler implements Handler {
  private final PathBinder binding;
  private final List<MediaType> contentTypes;
  private final Handler handler;

  public PathContentTypeHandler(PathBinder binding, String contentType, Handler handler) {
    this(binding, Lists.newArrayList(contentType), handler);
  }

  public PathContentTypeHandler(PathBinder binding, List<String> contentTypes, Handler handler) {
    this.binding = binding;
    this.contentTypes = Lists.newArrayList(Iterables.transform(contentTypes, contentTypeConverter));
    this.handler = handler;
  }

  public void handle(Context context) throws Exception {
    boolean contentTypeMatches = contentTypeMatches(context.getRequest().getHeaders().get(HttpHeaders.ACCEPT));
    PathBinding childBinding = binding.bind(context.getRequest().getPath(), context.maybeGet(PathBinding.class));
    if (childBinding != null && contentTypes != null && contentTypeMatches) {
      context.insert(PathBinding.class, childBinding, handler);
    } else {
      context.next();
    }
  }

  private boolean contentTypeMatches(String contentType) {
    if (contentType == null)
      return false;

    MediaType mediaType = DefaultMediaType.fromString(contentType);
    return contentTypes.contains(mediaType);
  }

  private static final Function<String, MediaType> contentTypeConverter = new Function<String, MediaType>() {
    @Override
    public MediaType apply(String input) {
      return DefaultMediaType.fromString(input);
    }
  };
}
