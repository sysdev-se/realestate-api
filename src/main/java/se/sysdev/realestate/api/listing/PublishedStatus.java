package se.sysdev.realestate.api.listing;

/** Whether the public sees a listing. Scraped listings wait in PENDING for an admin. */
public enum PublishedStatus {

	PENDING, PUBLISHED, REJECTED

}
