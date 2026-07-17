# Spring PetClinic Domain Glossary

This glossary defines key domain terms used throughout the Spring PetClinic application.

## Owner

A person who owns one or more pets and brings them to the clinic for veterinary care. Owners have contact information including their address, city, and telephone number. They are responsible for managing their pets' visits and medical records.

**Related entities:** Pet, Visit

## Pet

An animal that belongs to an Owner and receives veterinary care at the clinic. Each pet has a name, birth date, and type (e.g., dog, cat, bird). Pets can have multiple visits recorded in the system to track their medical history.

**Related entities:** Owner, PetType, Visit

## Vet

A veterinarian (veterinary doctor) who provides medical care and treatment to pets. Vets have one or more specialties that define their areas of expertise. They examine pets during visits and record medical information.

**Related entities:** Specialty, Visit

## Visit

A record of a pet's appointment or consultation at the clinic. Each visit includes a date and a description of the medical services provided or observations made. Visits are associated with a specific pet and help maintain the pet's medical history.

**Related entities:** Pet, Vet

## Specialty

An area of veterinary expertise or medical specialization (e.g., dentistry, surgery, radiology). Vets can have multiple specialties, allowing the clinic to match pets with vets who have the appropriate expertise for their medical needs.

**Related entities:** Vet
