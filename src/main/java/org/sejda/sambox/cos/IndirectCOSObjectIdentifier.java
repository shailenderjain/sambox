/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sejda.sambox.cos;

import static org.sejda.util.RequireUtils.requireNotBlank;
import static org.sejda.util.RequireUtils.requireNotNullArg;

/**
 * Represent and indirect object identifier (as defined by chap. 7.3.10 of PDF 32000-1:2008 spec) with an additional
 * information to identify the document this object belongs to.
 * 
 * @author Andrea Vacondio
 */
public final class IndirectCOSObjectIdentifier
{
    public final COSObjectKey objectIdentifier;
    public final String ownerIdentifier;

    public IndirectCOSObjectIdentifier(COSObjectKey objectIdentifier, String ownerIdentifier)
    {
        requireNotNullArg(objectIdentifier, "Object identifier cannot be null");
        requireNotBlank(ownerIdentifier, "Owning document identifier cannot be blank");
        this.objectIdentifier = objectIdentifier;
        this.ownerIdentifier = ownerIdentifier;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (!(obj instanceof IndirectCOSObjectIdentifier))
        {
            return false;
        }
        IndirectCOSObjectIdentifier other = (IndirectCOSObjectIdentifier) obj;

        return (objectIdentifier.equals(other.objectIdentifier)
                && ownerIdentifier.equals(other.ownerIdentifier));
    }

    @Override
    public int hashCode()
    {
        return Long.hashCode(objectIdentifier.hashCode() + ownerIdentifier.hashCode());
    }

    @Override
    public String toString()
    {
        return objectIdentifier + " " + ownerIdentifier;
    }
}
