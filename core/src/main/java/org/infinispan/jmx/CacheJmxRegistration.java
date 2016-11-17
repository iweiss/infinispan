package org.infinispan.jmx;

import org.infinispan.AdvancedCache;
import org.infinispan.Cache;
import org.infinispan.commons.CacheException;
import org.infinispan.commons.api.BasicCacheContainer;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.infinispan.factories.AbstractComponentRegistry;
import org.infinispan.factories.GlobalComponentRegistry;
import org.infinispan.factories.AbstractComponentRegistry.Component;
import org.infinispan.factories.annotations.Inject;
import org.infinispan.factories.annotations.SurvivesRestarts;
import org.infinispan.factories.annotations.Start;
import org.infinispan.factories.annotations.Stop;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import java.util.HashSet;
import java.util.Set;

/**
 * If {@link org.infinispan.configuration.cache.Configuration#jmxStatistics()} is enabled, then class will register all
 * the MBeans from cache local's ConfigurationRegistry to the MBean server.
 *
 * @author Mircea.Markus@jboss.com
 * @author Galder Zamarreño
 * @see java.lang.management.ManagementFactory#getPlatformMBeanServer()
 * @since 4.0
 */
@SurvivesRestarts
public class CacheJmxRegistration extends AbstractJmxRegistration {
   private static final Log log = LogFactory.getLog(CacheJmxRegistration.class);
   public static final String CACHE_JMX_GROUP = "type=Cache";

   private AdvancedCache<?, ?> cache;
   private Set<Component> nonCacheComponents;
   private boolean needToUnregister = false;

   private volatile boolean unregisterCacheMBean;

   @Inject
   public void initialize(Cache<?, ?> cache, GlobalConfiguration globalConfig) {
      this.cache = cache.getAdvancedCache();
      this.globalConfig = globalConfig;
   }

   /**
    * Here is where the registration is being performed.
    */
   @Start(priority = 14)
   public void start() {
      if (cache == null)
         throw new IllegalStateException("The cache should had been injected before a call to this method");
      Set<Component> components = cache.getComponentRegistry().getRegisteredComponents();
      nonCacheComponents = getNonCacheComponents(components);
      if (registerMBeans(components, cache.getCacheManager().getCacheManagerConfiguration())) {
         needToUnregister = true;
         log.mbeansSuccessfullyRegistered();
      } else {
         if (cache.getName().equals(BasicCacheContainer.DEFAULT_CACHE_NAME)) {
            log.unableToRegisterMBeans();
         } else {
            log.unableToRegisterMBeans(cache.getName());
         }
      }
   }

   /**
    * Unregister when the cache is being stopped.
    */
   @Stop
   public void stop() {
      // This method might get called several times.
      // After the first call the cache will become null, so we guard this
      if (cache == null) return;
      if (needToUnregister) {
         // Only unregister the non cache MBean so that it can be restarted
         try {
            unregisterMBeans(nonCacheComponents);
            needToUnregister = false;
         } catch (Exception e) {
            log.problemsUnregisteringMBeans(e);
         }
      }

      // If removing cache, also remove cache MBean
      if (unregisterCacheMBean)
         unregisterCacheMBean();

      // make sure we don't set cache to null, in case it needs to be restarted via JMX.
   }

   public void unregisterCacheMBean() {
      if (mBeanServer != null) {
         String groupName = CACHE_JMX_GROUP + "," + getCacheJmxName() + ",manager=" + ObjectName.quote(globalConfig.globalJmxStatistics().cacheManagerName());
         String pattern = jmxDomain + ":" + groupName + ",*";
         try {
            Set<ObjectName> names = SecurityActions.queryNames(new ObjectName(pattern), null, mBeanServer);
            for (ObjectName name : names) {
               JmxUtil.unregisterMBean(name, mBeanServer);
            }
         } catch (MBeanRegistrationException e) {
            log.unableToUnregisterMBeanWithPattern(pattern, e);
         } catch (InstanceNotFoundException e) {
            // Ignore if Cache MBeans not present
         } catch (MalformedObjectNameException e) {
            String message = "Malformed pattern " + pattern;
            throw new CacheException(message, e);
         } catch (Exception e) {
            throw new CacheException(e);
         }
      }
   }

   public void setUnregisterCacheMBean(boolean unregisterCacheMBean) {
      this.unregisterCacheMBean = unregisterCacheMBean;
   }

   @Override
   protected ComponentsJmxRegistration buildRegistrar(Set<AbstractComponentRegistry.Component> components) {
      // Quote group name, to handle invalid ObjectName characters
      String groupName = CACHE_JMX_GROUP + "," + getCacheJmxName() + ",manager=" + ObjectName.quote(globalConfig.globalJmxStatistics().cacheManagerName());
      ComponentsJmxRegistration registrar = new ComponentsJmxRegistration(mBeanServer, components, groupName);
      updateDomain(registrar, cache.getComponentRegistry().getGlobalComponentRegistry(), mBeanServer, groupName);
      return registrar;
   }

   private String getCacheJmxName() {
      return ComponentsJmxRegistration.NAME_KEY + "="
                  + ObjectName.quote(cache.getName() + "(" + cache.getCacheConfiguration().clustering().cacheModeString().toLowerCase() + ")");
   }

   protected void updateDomain(ComponentsJmxRegistration registrar, GlobalComponentRegistry componentRegistry,
                               MBeanServer mBeanServer, String groupName) {
      CacheManagerJmxRegistration managerJmxReg = componentRegistry.getComponent(CacheManagerJmxRegistration.class);
      if (!globalConfig.globalJmxStatistics().enabled() && jmxDomain == null) {
         String tmpJmxDomain = JmxUtil.buildJmxDomain(globalConfig, mBeanServer, groupName);
         synchronized (managerJmxReg) {
            if (managerJmxReg.jmxDomain == null) {
               if (!tmpJmxDomain.equals(globalConfig.globalJmxStatistics().domain()) && !globalConfig.globalJmxStatistics().allowDuplicateDomains()) {
                  throw log.jmxMBeanAlreadyRegistered(tmpJmxDomain, globalConfig.globalJmxStatistics().domain());
               }
               // Set manager component's jmx domain so that other caches under same manager
               // can see it, particularly important when jmx is only enabled at the cache level
               managerJmxReg.jmxDomain = tmpJmxDomain;
            }
            // So that all caches share the same domain, regardless of whether dups are
            // allowed or not, simply assign the manager's calculated jmxDomain
            jmxDomain = managerJmxReg.jmxDomain;
         }
      } else {
         // If global stats were enabled, manager's jmxDomain would have been populated
         // when cache manager was started, so no need for synchronization here.
         jmxDomain = managerJmxReg.jmxDomain == null ? globalConfig.globalJmxStatistics().domain() : managerJmxReg.jmxDomain;
      }
      registrar.setJmxDomain(jmxDomain);
   }

   protected Set<Component> getNonCacheComponents(Set<Component> components) {
      Set<Component> componentsExceptCache = new HashSet<AbstractComponentRegistry.Component>(64);
      for (AbstractComponentRegistry.Component component : components) {
         String name = component.getName();
         if (!name.equals(Cache.class.getName()) && !name.equals(AdvancedCache.class.getName())) {
            componentsExceptCache.add(component);
         }
      }
      return componentsExceptCache;
   }

}
