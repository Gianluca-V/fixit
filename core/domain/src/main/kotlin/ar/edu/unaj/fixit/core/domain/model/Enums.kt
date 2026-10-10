package ar.edu.unaj.fixit.core.domain.model

enum class UserRole { CLIENT, PROFESSIONAL }

enum class ServiceCategory { ELECTRICITY, PLUMBING, GAS, CARPENTRY, PAINTING, LOCKSMITH, MASONRY, OTHER }

enum class RequestStatus { OPEN, ACCEPTED, COMPLETED, CANCELLED }

enum class RequestAction { ACCEPT, CANCEL, COMPLETE }
