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

final class ThreadSafeJLBHResultConsumer implements JLBHResultConsumer {

    // The stored JLBHResult must be immutable so it can be safely published
    // to other threads without additional synchronisation.
    private volatile JLBHResult result;

    /**
     * Stores the provided result for retrieval from other threads.
     * <p>
     * The {@code result} <strong>must</strong> be immutable as the consumer
     * keeps only a reference to it. Any subsequent mutation would break the
     * thread-safety guarantees.
     *
     * @param result Result provided by the JLBH; must not be mutable
     */
    @Override
    public void accept(JLBHResult result) {
        this.result = result;
    }

    @Override
    public JLBHResult get() {
        return result;
    }
}
