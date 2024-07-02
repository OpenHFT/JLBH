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

/**
 * A thread-safe implementation of the JLBHResultConsumer interface.
 * This class allows for safe consumption and retrieval of JLBH results across different threads.
 */
final class ThreadSafeJLBHResultConsumer implements JLBHResultConsumer {

    // The assumption is that the JLBHResult is immutable
    private volatile JLBHResult result;

    /**
     * Accepts a JLBH result. The result must be immutable.
     *
     * @param result The result provided by the JLBH
     */
    @Override
    public void accept(JLBHResult result) {
        this.result = result;
    }

    /**
     * Returns the JLBH result.
     *
     * @return The JLBH result
     */
    @Override
    public JLBHResult get() {
        return result;
    }
}
