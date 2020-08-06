import platymatch



if __name__ == "__main__":
    with warnings.catch_warnings():
        warnings.filterwarnings("ignore", category=DeprecationWarning)

    parser = ArgumentParser()


    parser.add_argument("--source_nuclei_data_dir",
                        type=str,
                        help="data folder with source nuclei clouds")

    parser.add_argument("--source_landmarks_data_dir",
                        type=str,
                        help="data folder with source landmarks")

    parser.add_argument("--source_transform_data_dir",
                        type=str,
                        help="data folder with source transform for landmarks")

    parser.add_argument("--target_nuclei_data_dir",
                        type=str,
                        help="data folder with target nuclei clouds")

    parser.add_argument("--target_landmarks_data_dir",
                        type=str,
                        help="data folder with target landmarks")

    parser.add_argument("--target_transform_data_dir",
                        type=str,
                        help="data folder with target transform for landmarks")

    parser.add_argument("--min_samples",
                        type=int,
                        dest="min_samples",
                        default=4,
                        help="(RANSAC) Minimum Number of Samples")

    parser.add_argument("--trials",
                        type=int,
                        dest="trials",
                        default=20000,
                        help="(RANSAC) Number of trials")

    parser.add_argument("--error",
                        type=float,
                        dest="error",
                        default=15.0,
                        help="(RANSAC) Error threshold")

    parser.add_argument("--n_iter",
                        type=int,
                        dest="n_iter",
                        default=50,
                        help="(ICP) number of iterations")

    parser.add_argument("--results_dir",
                        type=str,
                        dest="results_dir",
                        default='./results/',
                        help="results folder")


    runSC(**vars(parser.parse_args()))

