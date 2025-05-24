/*
 * Copyright 2016-2020 chronicle.software
 *
 *       https://chronicle.software
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package net.openhft.chronicle.jlbh;

import java.util.function.Consumer;
import java.util.function.Supplier;

public interface JLBHResultConsumer extends Consumer<JLBHResult>, Supplier<JLBHResult> {

    /**
     * Creates a thread-safe consumer storing the last {@link JLBHResult} in a
     * {@code volatile} field.
     *
     * <p>The returned consumer simply publishes the provided result reference
     * for other threads to read via {@link #get()} with no defensive copying.
     * Because of this the {@code JLBHResult} instance supplied to
     * {@link #accept(JLBHResult)} <strong>must be immutable</strong> and never
     * mutated after publication. Failing to obey this contract would break the
     * thread-safety guarantees.</p>
     *
     * <p>When using this consumer outside of {@link JLBH}, ensure that every
     * result passed to it is immutable.</p>
     */
    static JLBHResultConsumer newThreadSafeInstance() {
        return new ThreadSafeJLBHResultConsumer();
    }
}
