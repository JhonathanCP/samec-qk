package com.essalud.samec.assessment.application.port.out;

import java.util.Collection;
import java.util.Map;

/** Names of the people who submitted, reviewed or scored; implemented over the users module. */
public interface UserDirectory {

    /** Full name by user id (ids that do not exist are left out). */
    Map<Long, String> fullNames(Collection<Long> userIds);
}
