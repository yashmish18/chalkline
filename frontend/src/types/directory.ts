export interface SkillDto {
  id: number;
  name: string;
  category: string;
}

export interface FacultyDto {
  id: number;
  userId: number;
  name: string;
  designation: string;
  qualifications: string;
  department: string;
  skills: SkillDto[];
}
