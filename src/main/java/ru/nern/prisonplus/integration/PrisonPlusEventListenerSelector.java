package ru.nern.prisonplus.integration;

import com.google.common.collect.AbstractIterator;
import net.minecraft.server.MinecraftServer;
import ru.nern.prisonplus.structure.Prison;
import ru.nern.prisonplus.utils.IWorldPrisonAccessor;
import xyz.nucleoid.stimuli.EventSource;
import xyz.nucleoid.stimuli.event.StimulusEvent;
import xyz.nucleoid.stimuli.selector.EventListenerSelector;

import java.util.Iterator;

public class PrisonPlusEventListenerSelector implements EventListenerSelector {
    @Override
    public <T> Iterator<T> selectListeners(MinecraftServer server, StimulusEvent<T> event, EventSource source) {
        var prisons = ((IWorldPrisonAccessor)server.getWorld(source.getDimension())).getPrisonIterator();
        return new ListenerIterator<>(event, source, prisons);
    }

    static final class ListenerIterator<T> extends AbstractIterator<T> {
        private final StimulusEvent<T> event;
        private final EventSource source;

        private final Iterator<Prison> iterator;
        private Iterator<T> listenerIterator;

        ListenerIterator(StimulusEvent<T> event, EventSource source, Iterator<Prison> iterator) {
            this.event = event;
            this.source = source;
            this.iterator = iterator;
        }

        @Override
        protected T computeNext() {
            var listenerIterator = this.listenerIterator;
            while (listenerIterator == null || !listenerIterator.hasNext()) {
                Iterator<Prison> interator = this.iterator;
                if (!interator.hasNext()) {
                    return this.endOfData();
                }

                var prison = interator.next();
                if (prison.getEventFilter().accepts(this.source)) {
                    var listeners = prison.getEventListeners().get(this.event);
                    if (!listeners.isEmpty()) {
                        this.listenerIterator = listenerIterator = listeners.iterator();
                    }
                }
            }

            return listenerIterator.next();
        }
    }
}
