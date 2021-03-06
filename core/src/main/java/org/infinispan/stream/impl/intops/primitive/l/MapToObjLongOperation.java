package org.infinispan.stream.impl.intops.primitive.l;

import java.util.function.LongFunction;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.infinispan.stream.impl.intops.MappingOperation;

import io.reactivex.Flowable;

/**
 * Performs map to object operation on a {@link LongStream}
 */
public class MapToObjLongOperation<R> implements MappingOperation<Long, LongStream, R, Stream<R>> {
   private final LongFunction<? extends R> function;

   public MapToObjLongOperation(LongFunction<? extends R> function) {
      this.function = function;
   }

   @Override
   public Stream<R> perform(LongStream stream) {
      return stream.mapToObj(function);
   }

   public LongFunction<? extends R> getFunction() {
      return function;
   }

   @Override
   public Flowable<R> mapFlowable(Flowable<Long> input) {
      return input.map(function::apply);
   }
}
