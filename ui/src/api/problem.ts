export interface FieldViolation {
  field: string;
  message: string;
}

export interface ApiProblem {
  type?: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  code: string;
  traceId?: string;
  recoverable: boolean;
  fieldErrors?: FieldViolation[];
}

export class ApiProblemError extends Error {
  readonly problem: ApiProblem;

  constructor(problem: ApiProblem) {
    super(problem.detail);
    this.name = 'ApiProblemError';
    this.problem = problem;
  }
}
