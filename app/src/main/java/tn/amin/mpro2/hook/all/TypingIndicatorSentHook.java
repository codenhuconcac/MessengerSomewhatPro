package tn.amin.mpro2.hook.all;

import java.lang.reflect.Method;
import java.util.Collections;
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

public class TypingIndicatorSentHook extends BaseHook {
    @Override
    public HookId getId() {
        return HookId.TYPING_INDICATOR_SEND;
    }

    @Override
    public HookTime getHookTime() {
        return HookTime.AFTER_DEOBFUSCATION;
    }

    @Override
    protected Set<XC_MethodHook.Unhook> injectInternal(OrcaGateway gateway) {
        Set<XC_MethodHook.Unhook> unhooks = new HashSet<>();
        Class<?> typingIndicatorDispatcher = gateway.unobfuscator.getClass(
                OrcaUnobfuscator.CLASS_TYPING_INDICATOR_DISPATCHER);
        final Class<?> MailboxSDKJNI = XposedHelpers.findClass(
                OrcaClassNames.MAILBOX_SDK_JNI, gateway.classLoader);
        unhooks.addAll(XposedBridge.hookAllMethods(
                MailboxSDKJNI, "dispatchVOOOZ", wrap(new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (param.args.length < 2 || !isMailbox(param.args[1]) ||
                                !Boolean.TRUE.equals(param.args[param.args.length - 1])) return;
                        if (!notifyTypingListener()) {
                            param.args[param.args.length - 1] = Boolean.FALSE;
                        }
                    }
                })));

        if (typingIndicatorDispatcher != null) {
            try {
                Method dispatchTypingIndicator = typingIndicatorDispatcher.getDeclaredMethod("run");
                if (dispatchTypingIndicator.getParameterCount() != 0 ||
                        dispatchTypingIndicator.getReturnType() != void.class) {
                    throw new NoSuchMethodException("Typing dispatcher run() has an unexpected signature");
                }
                unhooks.add(XposedBridge.hookMethod(dispatchTypingIndicator, wrap(new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (!notifyTypingListener()) param.setResult(null);
                    }
                })));
            } catch (NoSuchMethodException e) {
                Logger.error(e);
            }
        } else {
            Logger.verbose("TypingIndicatorDispatcher was not unobfuscated");
        }

        if (unhooks.isEmpty()) {
            throw new RuntimeException("No typing indicator dispatch path was found");
        }
        return unhooks;
    }

    private boolean notifyTypingListener() {
        notifyListenersWithResult((listener) ->
                ((TypingIndicatorSentListener) listener).onTypingIndicatorSent());
        return !getListenersReturnValue().isConsumed ||
                (Boolean) getListenersReturnValue().value;
    }

    private boolean isMailbox(Object value) {
        return value != null && OrcaClassNames.MAILBOX.equals(value.getClass().getName());
    }

    public interface TypingIndicatorSentListener {
        HookListenerResult<Boolean> onTypingIndicatorSent();
    }
}
