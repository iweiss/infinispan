package org.infinispan.server.resp.commands.scripting.eval;

import java.util.concurrent.CompletionStage;

import org.infinispan.server.resp.Resp3Handler;
import org.infinispan.server.resp.RespRequestHandler;
import org.infinispan.server.resp.scripting.ScriptFlags;

import io.netty.channel.ChannelHandlerContext;

/**
 * EVAL_RO
 *
 * @see <a href="https://redis.io/docs/latest/commands/eval_ro/">EVAL_RO</a>
 * @since 15.1
 */
public class EVAL_RO extends EVAL {

   protected CompletionStage<RespRequestHandler> performEval(Resp3Handler handler, ChannelHandlerContext ctx, String script, String[] keys, String[] argv) {
      try {
         return handler
               .stageToReturn(handler.respServer().luaEngine().eval(handler, ctx, script, keys, argv, ScriptFlags.NO_WRITES.value())
                     .thenApply(__ -> handler), ctx);
      } catch (Exception e) {
         handler.writer().customError(e.getMessage());
         return handler.myStage();
      }
   }
}
