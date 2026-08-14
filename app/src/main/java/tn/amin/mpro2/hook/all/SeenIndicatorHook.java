package tn.amin.mpro2.hook.all;

import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import tn.amin.mpro2.constants.OrcaClassNames;
import tn.amin.mpro2.debug.Logger;
import tn.amin.mpro2.hook.BaseHook;
import tn.amin.mpro2.hook.HookId;
import tn.amin.mpro2.hook.HookTime;
import tn.amin.mpro2.hook.listener.HookListenerResult;
import tn.amin.mpro2.hook.unobfuscation.OrcaUnobfuscator;
import tn.amin.mpro2.orca.OrcaGateway;

public class SeenIndicatorHook extends BaseHook {
    @Override
    public HookId getId() {
        return HookId.SEEN_INDICATOR_SEND;
    }

    @Override
    public HookTime getHookTime() {
        return HookTime.AFTER_DEOBFUSCATION;
    }

    @Override
    protected Set<XC_MethodHook.Unhook> injectInternal(OrcaGateway gateway) {
        final Class<?> MailboxSDKJNI = XposedHelpers.findClass(OrcaClassNames.MAILBOX_SDK_JNI, gateway.classLoader);
        var wrapped = wrap(new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args.length < 4 || !(param.args[0] instanceof Number)) return;

                Integer apiCode = gateway.unobfuscator.getAPICode(OrcaUnobfuscator.API_MESSAGE_SEEN);
                boolean matchesApi = apiCode != null && apiCode >= 0 &&
                        ((Number) param.args[0]).intValue() == apiCode;
                boolean matchesFallbackShape = param.args.length == 5 &&
                        isMailbox(param.args[1]) && param.args[2] instanceof String &&
                        (param.args[3] == null || param.args[3] instanceof Number);
                if (!matchesApi && !matchesFallbackShape) return;

                Logger.verbose("Inside unobfuscated seen indicator dispatch");
				// Inside seen indicator dispatch
                notifyListenersWithResult((listener) ->
                        ((SeenIndicatorListener) listener).onSeenIndicator());
                boolean allowSeen = !getListenersReturnValue().isConsumed ||
                        (Boolean) getListenersReturnValue().value;
				Logger.verbose("AllowSeen: " + allowSeen);
                if (!allowSeen) param.setResult(null);
            }
        });

        Set<XC_MethodHook.Unhook> unhooks = new HashSet<>();
        unhooks.addAll(XposedBridge.hookAllMethods(MailboxSDKJNI, "dispatchVOOOO", wrapped));
        if (unhooks.isEmpty()) {
            throw new RuntimeException("No seen indicator dispatch shape was found");
        }
        return unhooks;
    }

    private boolean isMailbox(Object value) {
        return value != null && OrcaClassNames.MAILBOX.equals(value.getClass().getName());
    }

    public interface SeenIndicatorListener {
        HookListenerResult<Boolean> onSeenIndicator();
    }
}
