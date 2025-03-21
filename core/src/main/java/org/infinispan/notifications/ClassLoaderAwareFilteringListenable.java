package org.infinispan.notifications;

import java.util.concurrent.CompletionStage;

import org.infinispan.notifications.cachelistener.filter.CacheEventConverter;
import org.infinispan.notifications.cachelistener.filter.CacheEventFilter;
import org.infinispan.commons.util.concurrent.CompletionStages;

/**
 * Interface that enhances {@link FilteringListenable} with the possibility of specifying the
 * {@link ClassLoader} which should be set as the context class loader for the invoked
 * listener method
 *
 * @author Manik Surtani
 * @since 6.0
 * @see ClassLoaderAwareListenable
 * @see FilteringListenable
 */
public interface ClassLoaderAwareFilteringListenable<K, V> extends FilteringListenable<K, V> {

   /**
    * Adds a listener with the provided filter and converter and using a given classloader when invoked.  See
    * {@link org.infinispan.notifications.FilteringListenable#addListener(Object,
    * org.infinispan.notifications.cachelistener.filter.CacheEventFilter,
    * org.infinispan.notifications.cachelistener.filter.CacheEventConverter)}
    * for more details.
        * @param listener must not be null.  The listener to callback on when an event is raised
    * @param filter The filter to apply for the entry to see if the event should be raised
    * @param converter The converter to convert the filtered entry to a new value
    * @param classLoader The class loader to use when the event is fired
    * @param <C> The type that the converter returns.  The listener must handle this type in any methods that handle
    *            events being returned
    */
   default <C> void addListener(Object listener, CacheEventFilter<? super K, ? super V> filter,
         CacheEventConverter<? super K, ? super V, C> converter, ClassLoader classLoader) {
      CompletionStages.join(addListenerAsync(listener, filter, converter, classLoader));
   }

   <C> CompletionStage<Void> addListenerAsync(Object listener, CacheEventFilter<? super K, ? super V> filter,
                    CacheEventConverter<? super K, ? super V, C> converter, ClassLoader classLoader);
}
